package com.game.scoobyjump

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(private val activity: Activity) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    private var lastInterstitialTimeMillis: Long = 0
    private val INTERSTITIAL_COOLDOWN_MILLIS: Long = 60000 // 60 seconds


    // Google Test Ad Unit IDs
    private val interstitialId = "ca-app-pub-3940256099942544/1033173712"
    private val rewardedId = "ca-app-pub-3940256099942544/5224354917"

    init {
        MobileAds.initialize(activity) {}
        loadInterstitialAd()
        loadRewardedAd()
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(activity, interstitialId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
            }
        })
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(activity, rewardedId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
            }
        })
    }

    fun showInterstitial(onAdDismissed: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialTimeMillis < INTERSTITIAL_COOLDOWN_MILLIS) {
            Log.d("AdManager", "Interstitial Ad skipped (cooldown).")
            onAdDismissed()
            return
        }

        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    lastInterstitialTimeMillis = System.currentTimeMillis()
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdDismissed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d("AdManager", "Interstitial Ad not ready/cooldown.")
            loadInterstitialAd()
            onAdDismissed() // fallback gracefully
        }
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun showRewardedAd(onRewardEarned: (Boolean) -> Unit) {
        if (rewardedAd != null) {
            var earnedReward = false
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                    onRewardEarned(earnedReward)
                }
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    rewardedAd = null
                    onRewardEarned(false)
                }
            }
            
            rewardedAd?.show(activity, OnUserEarnedRewardListener {
                earnedReward = true
            })
        } else {
            Log.d("AdManager", "Rewarded Ad not ready.")
            onRewardEarned(false) // fallback gracefully
        }
    }
}
