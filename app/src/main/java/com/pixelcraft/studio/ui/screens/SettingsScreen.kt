package com.pixelcraft.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixelcraft.studio.R
import com.pixelcraft.studio.core.ads.AdManager
import com.pixelcraft.studio.core.datastore.AppSettings
import com.pixelcraft.studio.core.datastore.SettingsDataStore
import com.pixelcraft.studio.core.ui.BottomBannerAd
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsDataStore: SettingsDataStore,
    adManager: AdManager,
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var selectedLang by remember(settings.languageCode) { mutableStateOf(settings.languageCode) }
    var forceRtlSwitch by remember(settings.forceRtl, isRtl) {
        mutableStateOf(settings.forceRtl ?: isRtl)
    }
    var selectedFormat by remember(settings.exportFormat) { mutableStateOf(settings.exportFormat) }
    var qualityValue by remember(settings.exportQuality) { mutableIntStateOf(settings.exportQuality) }

    fun persistLanguage(langCode: String, rtlOverride: Boolean?) {
        selectedLang = langCode
        scope.launch {
            settingsDataStore.setLanguage(code = langCode, rtlOverride = rtlOverride)
        }
    }

    fun persistExport(format: String, quality: Int) {
        selectedFormat = format
        qualityValue = quality
        scope.launch {
            settingsDataStore.setExportPreferences(format = format, quality = quality)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                BottomBannerAd(adManager = adManager)
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Language & RTL Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.language_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedLang == "system",
                                onClick = { persistLanguage("system", null) },
                                label = { Text(stringResource(R.string.lang_system)) }
                            )
                            FilterChip(
                                selected = selectedLang == "ar",
                                onClick = {
                                    forceRtlSwitch = true
                                    persistLanguage("ar", true)
                                },
                                label = { Text(stringResource(R.string.lang_arabic)) }
                            )
                            FilterChip(
                                selected = selectedLang == "en",
                                onClick = {
                                    forceRtlSwitch = false
                                    persistLanguage("en", false)
                                },
                                label = { Text(stringResource(R.string.lang_english)) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.force_rtl),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = forceRtlSwitch,
                                onCheckedChange = { checked ->
                                    forceRtlSwitch = checked
                                    persistLanguage(selectedLang, checked)
                                }
                            )
                        }
                    }
                }

                // Export Preferences Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.export_section),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedFormat.equals("PNG", ignoreCase = true),
                                onClick = { persistExport("PNG", qualityValue) },
                                label = { Text("PNG") }
                            )
                            FilterChip(
                                selected = selectedFormat.equals("JPG", ignoreCase = true),
                                onClick = { persistExport("JPG", qualityValue) },
                                label = { Text("JPG") }
                            )
                        }

                        Text(
                            text = stringResource(R.string.export_quality_label, qualityValue),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Slider(
                            value = qualityValue.toFloat(),
                            onValueChange = { raw ->
                                qualityValue = raw.roundToInt().coerceIn(50, 100)
                            },
                            onValueChangeFinished = {
                                persistExport(selectedFormat, qualityValue)
                            },
                            valueRange = 50f..100f
                        )
                    }
                }
            }
        }
    }
}
