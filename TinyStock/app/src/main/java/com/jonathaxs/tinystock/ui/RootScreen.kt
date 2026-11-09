package com.jonathaxs.tinystock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppProminentButton
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.IconBadge
import com.jonathaxs.tinystock.ui.components.RoundStepButton
import com.jonathaxs.tinystock.ui.components.RoundStepKind
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.theme.TinyStockTheme
import com.jonathaxs.tinystock.ui.util.ApplyOrientationLock

/**
 * Raiz do app. Por enquanto (A0) mostra uma tela provisoria com o tema e os componentes de estilo,
 * para conferir no emulador. Vira a escolha entre Welcome e Inicio quando essas telas forem portadas.
 */
@Composable
fun RootScreen() {
    ApplyOrientationLock()
    var units by rememberSaveable { mutableIntStateOf(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                AppCircleButton(AppIcons.BarChart, stringResource(R.string.tab_reports), onClick = {})
                AppCircleButton(Icons.Filled.DateRange, stringResource(R.string.tab_sales), onClick = {})
                AppCircleButton(Icons.Filled.Settings, stringResource(R.string.tab_settings), onClick = {})
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppSectionLabel(stringResource(R.string.preview_style_section))
                AppGroupedCard(
                    dividerInset = 64.dp,
                    rows = listOf(
                        { FeatureRow(Icons.Filled.ShoppingCart, Color(0xFFFF8D28), R.string.welcome_about_products_title, R.string.welcome_about_products_desc) },
                        { FeatureRow(Icons.Filled.DateRange, Color(0xFF0088FF), R.string.welcome_about_orders_title, R.string.welcome_about_orders_desc) },
                        { FeatureRow(AppIcons.BarChart, Color(0xFF34C759), R.string.welcome_about_reports_title, R.string.welcome_about_reports_desc) },
                    ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .appCard()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.home_box_info_units),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                RoundStepButton(RoundStepKind.Minus, onClick = { units-- }, enabled = units > 0)
                Text(
                    "$units",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 32.dp),
                )
                RoundStepButton(RoundStepKind.Plus, onClick = { units++ })
            }

            AppProminentButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_button_start), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_art),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.preview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, color: Color, titleRes: Int, descRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, color, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(2.dp))
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RootScreenPreview() {
    TinyStockTheme { RootScreen() }
}
