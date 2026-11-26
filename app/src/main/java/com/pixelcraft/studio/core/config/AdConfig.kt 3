package com.pixelcraft.studio.core.config

import com.pixelcraft.studio.BuildConfig

/**
 * Isolates official Google AdMob test IDs from production IDs.
 * Never block drawing, editing, or exporting workflows with ads.
 */
object AdConfig {
    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Replace with real AdMob Unit IDs only when publishing release builds
    private const val PROD_BANNER_AD_UNIT_ID = TEST_BANNER_AD_UNIT_ID
    private const val PROD_INTERSTITIAL_AD_UNIT_ID = TEST_INTERSTITIAL_AD_UNIT_ID

    val bannerUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID

    val interstitialUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_UNIT_ID else PROD_INTERSTITIAL_AD_UNIT_ID
}
