package com.pixelcraft.studio.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pixelCraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pixelcraft_settings"
)

data class AppSettings(
    val languageCode: String = "system", // "system", "ar", "en"
    val forceRtl: Boolean? = null,
    val exportQuality: Int = 95,
    val exportFormat: String = "PNG", // "PNG" or "JPG"
    val interstitialActionCount: Int = 0
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val FORCE_RTL = booleanPreferencesKey("force_rtl")
        val EXPORT_QUALITY = intPreferencesKey("export_quality")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val INTERSTITIAL_COUNT = intPreferencesKey("interstitial_action_count")
    }

    val settingsFlow: Flow<AppSettings> = context.pixelCraftDataStore.data.map { prefs ->
        AppSettings(
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "system",
            forceRtl = if (prefs.contains(Keys.FORCE_RTL)) prefs[Keys.FORCE_RTL] else null,
            exportQuality = (prefs[Keys.EXPORT_QUALITY] ?: 95).coerceIn(50, 100),
            exportFormat = prefs[Keys.EXPORT_FORMAT] ?: "PNG",
            interstitialActionCount = prefs[Keys.INTERSTITIAL_COUNT] ?: 0
        )
    }

    suspend fun setLanguage(code: String, rtlOverride: Boolean?) {
        context.pixelCraftDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_CODE] = code
            if (rtlOverride == null) {
                prefs.remove(Keys.FORCE_RTL)
            } else {
                prefs[Keys.FORCE_RTL] = rtlOverride
            }
        }
    }

    suspend fun setExportPreferences(format: String, quality: Int) {
        context.pixelCraftDataStore.edit { prefs ->
            prefs[Keys.EXPORT_FORMAT] = if (format.equals("JPG", ignoreCase = true)) "JPG" else "PNG"
            prefs[Keys.EXPORT_QUALITY] = quality.coerceIn(50, 100)
        }
    }

    suspend fun incrementInterstitialCounter(): Int {
        var updated = 0
        context.pixelCraftDataStore.edit { prefs ->
            updated = (prefs[Keys.INTERSTITIAL_COUNT] ?: 0) + 1
            prefs[Keys.INTERSTITIAL_COUNT] = updated
        }
        return updated
    }

    suspend fun resetInterstitialCounter() {
        context.pixelCraftDataStore.edit { prefs ->
            prefs[Keys.INTERSTITIAL_COUNT] = 0
        }
    }
}
