package com.jonathaxs.tinystock.ui.welcome

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonathaxs.tinystock.TinyStockApplication
import com.jonathaxs.tinystock.core.service.OrderReminderAuthorization
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppProminentButton
import com.jonathaxs.tinystock.ui.components.AppSecondaryButton
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.IconBadge
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.products.ProductFormScreen
import com.jonathaxs.tinystock.ui.products.ProductFormsViewModel
import com.jonathaxs.tinystock.ui.util.rememberIsTablet
import java.util.UUID

/**
 * Apresentacao da primeira abertura: Inicio > Sua loja > Primeiro produto > Variacoes > Tudo pronto, com o
 * formulario real do produto aberto pela dica de variacoes. Porte do `WelcomeView` do iOS (C3.2). Restaurar backup abre direto o seletor de arquivos (sem iCloud).
 * Na janela larga (tablet, dobravel aberto) o titulo da etapa e os botoes vao para um painel a esquerda (C9b).
 */
@Composable
fun WelcomeScreen(
    onComplete: () -> Unit,
    onBackupRestored: (UUID) -> Unit,
    viewModel: WelcomeViewModel = viewModel(),
) {
    val accent = MaterialTheme.colorScheme.primary
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }
    BackHandler(enabled = viewModel.step != WelcomeStep.Start) { viewModel.goBack() }
    val formAttempt = viewModel.productFormAttempt
    // Registrado depois: com o formulario aberto, voltar fecha so ele.
    BackHandler(enabled = formAttempt != null) { viewModel.productFormClosed() }
    val isTablet = rememberIsTablet()
    // Arquivos .json nem sempre vem marcados como JSON no Android.
    val onRestore = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Brilho suave da cor de destaque no topo.
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.22f), 0.5f to Color.Transparent)),
    ) {
        // Mesma regra de largura da Inicio (A11).
        val isWide = maxWidth >= 700.dp || (maxWidth >= 560.dp && isTablet)
        val panelWidth = if (maxWidth < 744.dp) 320.dp else 360.dp
        Row(Modifier.fillMaxSize()) {
            if (isWide) {
                WelcomeContextPanel(
                    step = viewModel.step,
                    continueLabel = continueLabel(viewModel.step, viewModel.storeName),
                    onContinue = { viewModel.advance(onComplete) },
                    onBack = viewModel::goBack,
                    onRestore = onRestore,
                    onSkip = viewModel::skipProductTour,
                    modifier = Modifier.width(panelWidth),
                )
                VerticalDivider()
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding(),
                ) {
                    WelcomeProgressBar(viewModel.steps, viewModel.step)
                    AnimatedContent(
                        targetState = viewModel.step,
                        transitionSpec = {
                            val forward = viewModel.isGoingForward
                            slideInHorizontally(tween(300)) { if (forward) it else -it } togetherWith
                                slideOutHorizontally(tween(300)) { if (forward) -it else it }
                        },
                        modifier = Modifier.weight(1f),
                        label = "welcomeStep",
                    ) { step ->
                        when (step) {
                            WelcomeStep.Start -> WelcomeStartStep(
                                onStart = { viewModel.advance(onComplete) },
                                onRestore = onRestore,
                                showsButtons = !isWide,
                            )
                            WelcomeStep.Store -> WelcomeStoreStep(
                                name = viewModel.storeName,
                                onNameChange = { viewModel.storeName = it },
                                onSubmit = { viewModel.advance(onComplete) },
                                showsHeader = !isWide,
                            )
                            WelcomeStep.Product -> WelcomeProductStep(showsHeader = !isWide)
                            WelcomeStep.Variants -> WelcomeVariantsStep(showsHeader = !isWide)
                            WelcomeStep.Ready -> WelcomeReadyStep(showsHeader = !isWide)
                        }
                    }
                    // No layout largo os botoes ficam no painel da esquerda.
                    if (viewModel.step != WelcomeStep.Start && !isWide) {
                        FooterButtons(
                            continueLabel = continueLabel(viewModel.step, viewModel.storeName),
                            showsSkip = viewModel.step.isProductTour,
                            onContinue = { viewModel.advance(onComplete) },
                            onBack = viewModel::goBack,
                            onSkip = viewModel::skipProductTour,
                        )
                    }
                }
            }
        }

        // Formulario real do primeiro produto, por cima das boas-vindas. Cada abertura tem o proprio estado.
        val formStoreID = viewModel.storeID
        if (formAttempt != null && formStoreID != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ProductFormScreen(
                    storeID = formStoreID,
                    productID = null,
                    initialFocus = null,
                    onClose = viewModel::productFormClosed,
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ProductFormsViewModel>(
                        key = "welcomeProductForm$formAttempt",
                    ),
                )
            }
        }

        if (viewModel.isRestoring) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
    }

    viewModel.pendingBackup?.let { payload ->
        val summary = payload.summary
        AlertDialog(
            onDismissRequest = viewModel::cancelBackup,
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_backup_import_confirm_message_v2,
                        summary.storeCount, summary.productCount, summary.variantCount, summary.orderCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreBackup(onBackupRestored) }) {
                    Text(stringResource(R.string.settings_backup_import_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelBackup) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    viewModel.backupError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupError,
            title = { Text(stringResource(R.string.settings_backup_error_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBackupError) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
    viewModel.storeError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStoreError,
            title = { Text(stringResource(R.string.store_error_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissStoreError) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
}

@Composable
private fun stepTitle(step: WelcomeStep): String = when (step) {
    WelcomeStep.Start -> stringResource(R.string.welcome_step_start_title)
    WelcomeStep.Store -> stringResource(R.string.welcome_step_store_title)
    WelcomeStep.Product -> stringResource(R.string.welcome_step_product_title)
    WelcomeStep.Variants -> stringResource(R.string.welcome_step_variants_title)
    WelcomeStep.Ready -> stringResource(R.string.welcome_step_ready_title)
}

@Composable
private fun stepSubtitle(step: WelcomeStep): String = when (step) {
    WelcomeStep.Start -> stringResource(R.string.welcome_step_start_subtitle)
    WelcomeStep.Store -> stringResource(R.string.welcome_step_store_subtitle)
    WelcomeStep.Product -> stringResource(R.string.welcome_step_product_subtitle)
    WelcomeStep.Variants -> stringResource(R.string.welcome_step_variants_subtitle)
    WelcomeStep.Ready -> stringResource(R.string.welcome_step_ready_subtitle)
}

@Composable
private fun continueLabel(step: WelcomeStep, storeName: String): String = when {
    step == WelcomeStep.Store && storeName.isBlank() -> stringResource(R.string.welcome_button_skip)
    step == WelcomeStep.Product -> stringResource(R.string.welcome_button_addNow)
    step == WelcomeStep.Variants -> stringResource(R.string.welcome_button_addProduct)
    step == WelcomeStep.Ready -> stringResource(R.string.welcome_button_finish)
    else -> stringResource(R.string.welcome_button_continue)
}

// region Partes

/** Barra de etapas em capsulas; a atual fica mais longa. */
@Composable
private fun WelcomeProgressBar(steps: List<WelcomeStep>, step: WelcomeStep) {
    val total = steps.size
    val current = steps.indexOf(step) + 1
    val label = stringResource(R.string.a11y_welcome_progress, current, total)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = AppStyle.HorizontalPadding)
            .padding(top = 12.dp)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until total) {
            val color = if (index < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
            val isCurrent = index == current - 1
            Box(
                Modifier
                    .then(if (isCurrent) Modifier.weight(1f) else Modifier.width(22.dp))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Emoji num circulo com tom da cor de destaque, titulo e subtitulo da etapa. */
@Composable
private fun WelcomeStepHeader(step: WelcomeStep, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Text(step.emoji, fontSize = 44.sp) }
        Text(
            title,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // No Inicio a frase fica estreita, pouco mais larga que o botao Comecar.
            modifier = if (step == WelcomeStep.Start) Modifier.widthIn(max = 300.dp) else Modifier,
        )
    }
}

/** Conteudo rolavel, centralizado na altura quando cabe. */
@Composable
private fun StepScroll(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/**
 * Painel a esquerda no layout largo: cabecalho da etapa atual e os botoes no rodape. No Inicio fica so o par
 * Comecar / Restaurar backup, centralizado, porque o "Ola!" ja esta no conteudo a direita.
 */
@Composable
private fun WelcomeContextPanel(
    step: WelcomeStep,
    continueLabel: String,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStart = step == WelcomeStep.Start
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Crossfade(targetState = step, animationSpec = tween(300), label = "welcomePanelHeader") { current ->
                if (current != WelcomeStep.Start) {
                    WelcomeStepHeader(current, stepTitle(current), stepSubtitle(current))
                }
            }
        }
        Column(
            Modifier
                .then(if (isStart) Modifier.width(260.dp) else Modifier.fillMaxWidth())
                .padding(bottom = if (isStart) 0.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppProminentButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isStart) stringResource(R.string.welcome_button_start) else continueLabel,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                )
            }
            AppSecondaryButton(onClick = if (isStart) onRestore else onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (isStart) R.string.welcome_start_restore else R.string.welcome_button_back),
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                )
            }
            if (step.isProductTour) SkipButton(onSkip)
        }
        // No Inicio um espaco igual ao de cima centraliza os botoes.
        if (isStart) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FooterButtons(
    continueLabel: String,
    showsSkip: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyle.HorizontalPadding)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppProminentButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(continueLabel, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        AppSecondaryButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_button_back), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        if (showsSkip) SkipButton(onSkip)
    }
}

/** Pular das dicas do primeiro produto: vai direto para o fim. */
@Composable
private fun SkipButton(onSkip: () -> Unit) {
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.welcome_button_skip), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

// endregion

// region Etapas

@Composable
private fun WelcomeStartStep(onStart: () -> Unit, onRestore: () -> Unit, showsButtons: Boolean) {
    StepScroll {
        WelcomeStepHeader(
            WelcomeStep.Start,
            stringResource(R.string.welcome_step_start_title),
            stringResource(R.string.welcome_step_start_subtitle),
        )
        // No layout largo os botoes ficam no painel da esquerda.
        if (showsButtons) {
            Spacer(Modifier.size(28.dp))
            // No Inicio os botoes ficam logo abaixo do "Ola!", mais estreitos.
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppProminentButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.welcome_button_start), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                AppSecondaryButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.welcome_start_restore), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
            }
        }
    }
}

/** Dica do que o formulario pede, antes de abrir o de verdade. */
@Composable
private fun WelcomeProductStep(showsHeader: Boolean) {
    StepScroll {
        // No layout largo o cabecalho fica no painel da esquerda.
        if (showsHeader) {
            WelcomeStepHeader(WelcomeStep.Product, stepTitle(WelcomeStep.Product), stepSubtitle(WelcomeStep.Product))
            Spacer(Modifier.size(20.dp))
        }
        AppGroupedCard(
            dividerInset = 66.dp,
            rows = listOf(
                { TipRow(AppIcons.Camera, Color(0xFFFF9500), R.string.welcome_product_photo_title, R.string.welcome_product_photo_desc) },
                { TipRow(AppIcons.Money, Color(0xFF34C759), R.string.welcome_product_price_title, R.string.welcome_product_price_desc) },
                { TipRow(AppIcons.Box, Color(0xFF007AFF), R.string.welcome_product_stock_title, R.string.welcome_product_stock_desc) },
            ),
        )
    }
}

/** Com ou sem variacoes, com o exemplo dos tamanhos de uma camiseta. */
@Composable
private fun WelcomeVariantsStep(showsHeader: Boolean) {
    val sizes = stringResource(R.string.welcome_variants_sizes).split(",")
    StepScroll {
        if (showsHeader) {
            WelcomeStepHeader(WelcomeStep.Variants, stepTitle(WelcomeStep.Variants), stepSubtitle(WelcomeStep.Variants))
            Spacer(Modifier.size(20.dp))
        }
        AppGroupedCard(
            dividerInset = 66.dp,
            rows = listOf(
                {
                    TipRow(AppIcons.Layers, Color(0xFFAF52DE), R.string.welcome_variants_with_title, R.string.welcome_variants_with_desc) {
                        SizeChips(sizes.zip(listOf(3, 5, 2)))
                    }
                },
                { TipRow(Icons.Filled.Check, Color(0xFF8E8E93), R.string.welcome_variants_without_title, R.string.welcome_variants_without_desc) },
            ),
        )
    }
}

/** P, M e G com a quantidade de cada um, so ilustrando. */
@Composable
private fun SizeChips(sizes: List<Pair<String, Int>>) {
    Row(
        Modifier.padding(top = 6.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((size, quantity) in sizes) {
            Text(
                "$size · $quantity",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Icone colorido, titulo e uma frase curta, como nas apresentacoes do sistema. */
@Composable
private fun TipRow(icon: ImageVector, color: Color, title: Int, desc: Int, accessory: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, color, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            accessory()
        }
    }
}

@Composable
private fun WelcomeStoreStep(name: String, onNameChange: (String) -> Unit, onSubmit: () -> Unit, showsHeader: Boolean) {
    val label = stringResource(R.string.welcome_store_field)
    StepScroll {
        if (showsHeader) {
            WelcomeStepHeader(
                WelcomeStep.Store,
                stringResource(R.string.welcome_step_store_title),
                stringResource(R.string.welcome_step_store_subtitle),
            )
            Spacer(Modifier.size(20.dp))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppSectionLabel(label)
            Box(
                Modifier
                    .fillMaxWidth()
                    .appCard()
                    .padding(16.dp),
            ) {
                if (name.isEmpty()) {
                    Text(stringResource(R.string.store_default_name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

private enum class PermissionState { Idle, Allowed, Denied }

/** Pedido opcional dos lembretes de producao e despacho. Recusar nao bloqueia nada (como no iOS). */
@Composable
private fun WelcomeReadyStep(showsHeader: Boolean) {
    val context = LocalContext.current
    val reminders = remember { (context.applicationContext as TinyStockApplication).container.reminders }
    val reminderState by reminders.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { reminders.onPermissionResult() }
    }
    // Volta dos ajustes do sistema com a permissao mudada.
    LifecycleResumeEffect(Unit) {
        reminders.requestRefresh()
        onPauseOrDispose { }
    }
    // Permitida so conta com os lembretes ligados: antes do Android 13 a permissao ja vem liberada.
    val state = when (reminderState.authorization) {
        OrderReminderAuthorization.Authorized, OrderReminderAuthorization.Provisional ->
            if (reminderState.settings.isEnabled) PermissionState.Allowed else PermissionState.Idle
        OrderReminderAuthorization.Denied -> PermissionState.Denied
        else -> PermissionState.Idle
    }
    StepScroll {
        if (showsHeader) {
            WelcomeStepHeader(
                WelcomeStep.Ready,
                stringResource(R.string.welcome_step_ready_title),
                stringResource(R.string.welcome_step_ready_subtitle),
            )
            Spacer(Modifier.size(20.dp))
        }
        PermissionCard(
            icon = Icons.Filled.Notifications,
            iconColor = Color(0xFFFF3B30),
            title = stringResource(R.string.welcome_ready_notifications_title),
            desc = stringResource(R.string.welcome_ready_notifications_desc),
            state = state,
            onAllow = {
                scope.launch {
                    reminders.setEnabled(true)
                    val notAsked = reminders.state.value.authorization == OrderReminderAuthorization.NotDetermined
                    if (notAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    desc: String,
    state: PermissionState,
    onAllow: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .appCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, iconColor, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (state) {
            PermissionState.Idle -> AppProminentButton(onClick = onAllow, large = false) {
                Text(stringResource(R.string.welcome_ready_allow), fontWeight = FontWeight.SemiBold)
            }
            PermissionState.Allowed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.welcome_ready_allowed), color = Color(0xFF34C759), fontWeight = FontWeight.Medium)
            }
            PermissionState.Denied -> Text(
                stringResource(R.string.welcome_ready_denied),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// endregion
