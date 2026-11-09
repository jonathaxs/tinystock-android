package com.jonathaxs.tinystock.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Guarda as fotos de lojas, produtos e variacoes. No iOS a foto fica no banco (`imageData`); aqui o
 * banco guarda so o nome do arquivo ([com.jonathaxs.tinystock.core.model.Product.imageFileName]).
 */
interface ImageFileStore {
    /** Bytes da foto, ou null se o arquivo nao existe mais. */
    suspend fun read(fileName: String): ByteArray?

    /** Grava num arquivo novo e devolve o nome dele. Nunca sobrescreve outro arquivo. */
    suspend fun write(bytes: ByteArray): String

    /** Apaga o arquivo. Nome inexistente nao faz nada. */
    suspend fun delete(fileName: String)
}

/** Fotos como arquivos soltos numa pasta do app (por exemplo `filesDir/images`). */
class DirectoryImageFileStore(private val directory: File) : ImageFileStore {

    override suspend fun read(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(fileName) ?: return@withContext null
        if (file.isFile) file.readBytes() else null
    }

    override suspend fun write(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        // Grava num temporario e renomeia, para nao deixar foto pela metade se o app fechar no meio.
        val temporary = File(directory, "$name.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(File(directory, name))) { "Nao foi possivel gravar a foto" }
        name
    }

    override suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { fileFor(fileName)?.delete() }
    }

    /** Aceita so nome simples, sem pasta, para nao ler nem apagar fora do diretorio. */
    private fun fileFor(fileName: String): File? =
        fileName.takeIf { it.isNotEmpty() && it == File(it).name && it != "." && it != ".." }
            ?.let { File(directory, it) }
}

/** Versao em memoria para testes e previas. */
class InMemoryImageFileStore : ImageFileStore {
    val files = LinkedHashMap<String, ByteArray>()

    override suspend fun read(fileName: String): ByteArray? = files[fileName]?.copyOf()

    override suspend fun write(bytes: ByteArray): String {
        val name = "${UUID.randomUUID()}.jpg"
        files[name] = bytes.copyOf()
        return name
    }

    override suspend fun delete(fileName: String) {
        files.remove(fileName)
    }
}
