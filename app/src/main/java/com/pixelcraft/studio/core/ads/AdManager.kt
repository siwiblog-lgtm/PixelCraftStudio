package com.pixelcraft.studio.core.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.pixelcraft.studio.core.config.AdConfig

class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    fun initializeSdk(onComplete: (() -> Unit)? = null) {
        MobileAds.initialize(context.applicationContext) {
            preloadInterstitial()
            onComplete?.invoke()
        }
    }

    fun createBannerView(adSize: AdSize = AdSize.BANNER): AdView {
        return AdView(context).apply {
            setAdSize(adSize)
            adUnitId = AdConfig.bannerUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    fun preloadInterstitial() {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true

        InterstitialAd.load(
            context.applicationContext,
            AdConfig.interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    fun showInterstitialIfReady(
        activity: Activity,
        onDismissOrUnavailable: () -> Unit
    ) {
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial()
            onDismissOrUnavailable()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial()
                onDismissOrUnavailable()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial()
                onDismissOrUnavailable()
            }
        }

        ad.show(activity)
    }
}
