#!/usr/bin/env python3
"""
Converte os textos do app iOS (Localizable.strings, en e pt-BR) para recursos do Android.

Gera `values/strings_ios.xml` e `values-pt-rBR/strings_ios.xml` no modulo :app. Nao editar esses
arquivos a mao: rodar o script de novo quando os textos do iOS mudarem.

- Chave: o "." do iOS vira "_" (`home.box.info.units` > `home_box_info_units`).
- Formato: `%@` vira `%s`, `%lld` vira `%d`. Com mais de um argumento, os sem posicao ganham posicao
  (`%1$s`, `%2$s`), como o Android exige.
- Texto que precisa ser diferente no Android (iCloud, iPhone...) fica no `strings.xml` da mesma pasta,
  com a mesma chave: o script pula toda chave que ja existe la.

Uso, a partir de git-android/:
    python3 scripts/import_ios_strings.py [caminho/para/git-ios]
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IOS = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "git-ios"
SOURCE = IOS / "TinyStock/TinyStockCore/Sources/TinyStockCore/Resources/Localizations"
RES = ROOT / "TinyStock/app/src/main/res"
LOCALES = {"en": "values", "pt-BR": "values-pt-rBR"}

ENTRY = re.compile(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;\s*$')
MARK = re.compile(r"^\s*/\*\s*MARK:\s*-\s*(.*?)\s*\*/\s*$")
SPEC = re.compile(r"%%|%(?:(\d+)\$)?(?:ll|l)?([@dfs])")
NAME = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")


def unescape_ios(text):
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), text)


def parse(path):
    """Lista de ("section", titulo) e ("entry", chave, valor), na ordem do arquivo."""
    items = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if m := MARK.match(line):
            items.append(("section", m.group(1)))
        elif m := ENTRY.match(line):
            items.append(("entry", m.group(1), unescape_ios(m.group(2))))
        elif line.lstrip().startswith('"'):
            sys.exit(f"{path}:{number}: linha nao reconhecida")
    return items


def convert_format(text):
    """Devolve (texto no formato Android, assinatura dos argumentos, tem % solto)."""
    loose = [m for m in SPEC.finditer(text) if m.group(0) != "%%" and m.group(1) is None]
    renumber = len(loose) > 1
    counter = 0
    signature = []

    def replace(m):
        nonlocal counter
        if m.group(0) == "%%":
            return "%%"
        kind = "s" if m.group(2) == "@" else m.group(2)
        position = m.group(1)
        if position is None and renumber:
            counter += 1
            position = str(counter)
        signature.append((position, kind))
        return f"%{position}${kind}" if position else f"%{kind}"

    converted = SPEC.sub(replace, text)
    stray_percent = "%" in SPEC.sub("", text)
    return converted, sorted(signature, key=lambda s: (s[0] or "", s[1])), stray_percent


def escape_android(text):
    text = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;")
    text = text.replace('"', '\\"').replace("'", "\\'").replace("\n", "\\n").replace("\t", "\\t")
    if text[:1] in ("@", "?"):
        text = "\\" + text
    return text


def manual_keys(folder):
    path = RES / folder / "strings.xml"
    if not path.exists():
        return set()
    return {node.get("name") for node in ET.parse(path).getroot().iter("string")}


def main():
    parsed = {locale: parse(SOURCE / f"{locale}.lproj/Localizable.strings") for locale in LOCALES}
    entries = {locale: {i[1]: i[2] for i in items if i[0] == "entry"} for locale, items in parsed.items()}

    keys = {locale: set(values) for locale, values in entries.items()}
    if keys["en"] != keys["pt-BR"]:
        sys.exit(f"Chaves diferentes entre en e pt-BR: {sorted(keys['en'] ^ keys['pt-BR'])}")

    names = {}
    for key in entries["en"]:
        name = key.replace(".", "_")
        if not NAME.match(name):
            sys.exit(f"Chave sem nome valido no Android: {key}")
        if name in names:
            sys.exit(f"Duas chaves viram o mesmo nome: {names[name]} e {key}")
        names[name] = key

    converted = {locale: {} for locale in LOCALES}
    for key in entries["en"]:
        signatures = set()
        for locale in LOCALES:
            text, signature, stray = convert_format(entries[locale][key])
            converted[locale][key] = (text, stray)
            signatures.add(tuple(signature))
        if len(signatures) > 1:
            sys.exit(f"Argumentos diferentes entre en e pt-BR em {key}")

    for locale, folder in LOCALES.items():
        skip = manual_keys(folder)
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!-- Gerado por scripts/import_ios_strings.py a partir dos textos do app iOS. Nao editar a mao:",
            "     para mudar um texto so no Android, defina a mesma chave em strings.xml. -->",
            "<resources>",
        ]
        written = 0
        for item in parsed[locale]:
            if item[0] == "section":
                lines += ["", f"    <!-- {item[1]} -->"]
                continue
            key = item[1]
            name = key.replace(".", "_")
            if name in skip:
                continue
            text, stray = converted[locale][key]
            attribute = ' formatted="false"' if stray else ""
            lines.append(f'    <string name="{name}"{attribute}>{escape_android(text)}</string>')
            written += 1
        lines.append("</resources>")
        target = RES / folder / "strings_ios.xml"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{target.relative_to(ROOT)}: {written} textos ({len(skip & set(names))} vem do strings.xml)")


if __name__ == "__main__":
    main()
