package com.pixelcraft.studio

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pixelcraft.studio.core.ads.AdManager
import com.pixelcraft.studio.core.datastore.AppSettings
import com.pixelcraft.studio.core.datastore.SettingsDataStore
import com.pixelcraft.studio.core.theme.PixelCraftTheme
import com.pixelcraft.studio.data.LocalStorageManager
import com.pixelcraft.studio.model.PixelCanvasDocument
import com.pixelcraft.studio.ui.screens.EditorScreen
import com.pixelcraft.studio.ui.screens.HomeScreen
import com.pixelcraft.studio.ui.screens.SettingsScreen
import java.util.Locale

private enum class AppRoute {
    HOME,
    EDITOR,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var storageManager: LocalStorageManager
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsDataStore = SettingsDataStore(applicationContext)
        storageManager = LocalStorageManager(applicationContext)
        adManager = AdManager(this)
        adManager.initializeSdk()

        setContent {
            PixelCraftApp(
                settingsDataStore = settingsDataStore,
                storageManager = storageManager,
                adManager = adManager
            )
        }
    }
}

@Composable
private fun PixelCraftApp(
    settingsDataStore: SettingsDataStore,
    storageManager: LocalStorageManager,
    adManager: AdManager
) {
    val baseContext = LocalContext.current
    val settings by settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())

    var currentRoute by remember { mutableStateOf(AppRoute.HOME) }
    var activeDocument by remember { mutableStateOf<PixelCanvasDocument?>(null) }

    val localizedContext = remember(baseContext, settings.languageCode) {
        createLocalizedContext(baseContext, settings.languageCode)
    }

    val isRtl = remember(localizedContext, settings.forceRtl) {
        settings.forceRtl ?: (
            localizedContext.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
            )
    }

    BackHandler(enabled = currentRoute != AppRoute.HOME) {
        currentRoute = AppRoute.HOME
    }

    PixelCraftTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalContext provides localizedContext) {
            when (currentRoute) {
                AppRoute.HOME -> {
                    HomeScreen(
                        storageManager = storageManager,
                        adManager = adManager,
                        isRtl = isRtl,
                        onOpenDocument = { doc ->
                            activeDocument = doc
                            currentRoute = AppRoute.EDITOR
                        },
                        onOpenSettings = {
                            currentRoute = AppRoute.SETTINGS
                        }
                    )
                }

                AppRoute.EDITOR -> {
                    val doc = activeDocument
                    if (doc != null) {
                        EditorScreen(
                            initialDocument = doc,
                            settings = settings,
                            settingsDataStore = settingsDataStore,
                            storageManager = storageManager,
                            adManager = adManager,
                            isRtl = isRtl,
                            onNavigateBack = {
                                currentRoute = AppRoute.HOME
                            }
                        )
                    } else {
                        currentRoute = AppRoute.HOME
                    }
                }

                AppRoute.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        settingsDataStore = settingsDataStore,
                        adManager = adManager,
                        isRtl = isRtl,
                        onNavigateBack = {
                            currentRoute = AppRoute.HOME
                        }
                    )
                }
            }
        }
    }
}

private fun createLocalizedContext(context: Context, languageCode: String): Context {
    if (languageCode == "system" || languageCode.isBlank()) {
        return context
    }
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return context.createConfigurationContext(config)
}
