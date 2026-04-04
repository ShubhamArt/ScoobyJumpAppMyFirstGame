package com.game.scoobyjump

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    
    // Screens
    private lateinit var homeScreen: View
    private lateinit var hudScreen: View
    private lateinit var gameOverScreen: View
    
    // UI Elements
    private lateinit var homeCoinsText: TextView
    private lateinit var hudScore: TextView
    private lateinit var hudCoinsText: TextView
    private lateinit var hudSpiritMeter: ProgressBar
    private lateinit var btnTriggerOverdrive: Button
    private lateinit var pauseBtn: Button
    private lateinit var playerPreviewImage: View
    private lateinit var playBtn: Button
    private lateinit var settingsBtn: ImageButton
    private lateinit var shopBtn: ImageButton
    private lateinit var missionsBtn: ImageButton
    private lateinit var missionsBadge: TextView
    private lateinit var leaderboardBtn: ImageButton

    // Managers
    private lateinit var audioManager: AudioManager
    private lateinit var adManager: AdManager
    private lateinit var saveManager: SaveManager
    private lateinit var currencyManager: CurrencyManager
    private lateinit var missionManager: MissionManager
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var localHistoryManager: LocalHistoryManager
    private val skinManager = SkinManager()
    
    private var currentShopIndex = 0
    private var hasUsedRevive = false
    private var isCountdownActive = false
    private var countdownAnimator: android.animation.ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val save = SaveManager(applicationContext)
            save.saveString("last_crash", exception.stackTraceToString())
            defaultHandler?.uncaughtException(thread, exception)
        }

        saveManager = SaveManager(this)
        currencyManager = CurrencyManager(saveManager)
        missionManager = MissionManager(saveManager, currencyManager)
        analyticsManager = AnalyticsManager(saveManager)
        localHistoryManager = LocalHistoryManager(this)
        
        audioManager = AudioManager(this)
        adManager = AdManager(this)

        gameView = GameView(this)
        gameView.audioManager = audioManager
        gameView.missionManager = missionManager
        gameView.currencyManager = currencyManager
        gameView.analyticsManager = analyticsManager
        gameView.localHistoryManager = localHistoryManager
        
        val container = findViewById<FrameLayout>(R.id.game_view_container)
        container.addView(gameView)

        homeScreen = findViewById(R.id.home_screen)
        hudScreen = findViewById(R.id.hud_screen)
        homeCoinsText = findViewById(R.id.home_coins_text)
        hudScore = findViewById(R.id.hud_score)
        hudCoinsText = findViewById(R.id.hud_coins)
        hudSpiritMeter = findViewById(R.id.hud_spirit_meter)
        btnTriggerOverdrive = findViewById(R.id.btn_trigger_overdrive)
        pauseBtn = findViewById(R.id.pause_button)
        playerPreviewImage = findViewById(R.id.player_preview_image)
        playBtn = findViewById(R.id.play_button)
        settingsBtn = findViewById(R.id.settings_button)
        shopBtn = findViewById(R.id.shop_button)
        missionsBtn = findViewById(R.id.missions_button)
        missionsBadge = findViewById(R.id.missions_badge)
        leaderboardBtn = findViewById(R.id.leaderboard_button)

        missionManager.onMissionCompleted = { mission ->
            runOnUiThread {
                android.widget.Toast.makeText(this, "Mission Complete: ${mission.title}! +${mission.reward} Coins", android.widget.Toast.LENGTH_LONG).show()
                audioManager.playChaChing()
                currencyManager.addCoins(mission.reward)
                homeCoinsText.text = " ${currencyManager.getCoins()}"
                hudCoinsText.text = " ${currencyManager.getCoins()}"
                updateMissionsBadge()
                
                if (mission.unlocksSkin != -1 && !saveManager.getBoolean("skin_unlocked_${mission.unlocksSkin}", false)) {
                    saveManager.saveBoolean("skin_unlocked_${mission.unlocksSkin}", true)
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("LEGENDARY UNLOCK!")
                        .setMessage("You've unlocked the Ghost Edition Hero!")
                        .setPositiveButton("AWESOME!", null)
                        .show()
                }
            }
        }
        
        missionManager.onStreakUnlockedCallback = {
            runOnUiThread {
                android.app.AlertDialog.Builder(this)
                    .setTitle("LEGENDARY UNLOCK!")
                    .setMessage("5-DAY STREAK COMPLETED!\n\nThe exclusive Chrome Edition Hero is now permanently unlocked in your Hero Shop!")
                    .setPositiveButton("AWESOME!", null)
                    .setCancelable(false)
                    .show()
                audioManager.playChaChing()
            }
        }

        setupGameCallbacks()
        setupClickListeners()
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (gameView.gameState) {
                    GameState.PLAYING -> {
                        gameView.gameState = GameState.PAUSED
                        audioManager.pauseBGM()
                        showPauseDialog()
                    }
                    GameState.HOME -> {
                        showConfirmExitDialog()
                    }
                    GameState.PAUSED -> {
                        // Require user interaction via Pause Panel buttons
                    }
                    else -> {
                        // Game Over or Revive screens, ignore hardware back button
                    }
                }
            }
        })

        audioManager.isSoundEnabled = saveManager.getBoolean("sound_enabled", true)
        updateSettingsBtnText()
        startPlayerDancing()

        showHomeScreen()
        updateMissionsBadge()
    }
    
    private fun updateMissionsBadge() {
        if (missionManager.missions.any { it.completed && !it.claimed }) {
            missionsBadge.visibility = View.VISIBLE
        } else {
            missionsBadge.visibility = View.GONE
        }
    }

    private fun setupGameCallbacks() {
        gameView.onScoreChange = { score ->
            runOnUiThread {
                hudScore.text = " $score"
            }
        }
        
        gameView.onCoinsChange = { coins ->
            runOnUiThread {
                hudCoinsText.text = " $coins"
            }
        }

        gameView.onSpiritChargeChange = { charge ->
            runOnUiThread {
                hudSpiritMeter.progress = charge
                if (charge >= 100) {
                    btnTriggerOverdrive.visibility = View.VISIBLE
                } else {
                    btnTriggerOverdrive.visibility = View.GONE
                }
            }
        }

        gameView.onGameOver = { finalScore ->
            runOnUiThread {
                val highScore = localHistoryManager.getHighestAltitude()
                if (finalScore > highScore && finalScore > 0) {
                    val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    val pbKey = "pb_count_$today"
                    var pbCount = saveManager.getInt(pbKey, 0)
                    pbCount++
                    saveManager.saveInt(pbKey, pbCount)
                    if (pbCount == 3) {
                        Toast.makeText(this@MainActivity, "HIDDEN MISSION: Beat PB 3 Times! +1000 Coins", Toast.LENGTH_LONG).show()
                        currencyManager.addCoins(1000)
                        audioManager.playChaChing()
                        gameView.onCoinsChange?.invoke(currencyManager.getCoins())
                    }
                }

                if (!hasUsedRevive) {
                    showReviveCountdownDialog(finalScore)
                } else {
                    showGameOverScreen(finalScore)
                    if (Math.random() < 0.3) {
                        gameView.suspendMainLoop()
                        adManager.showInterstitial {
                            gameView.resumeMainLoop()
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        playBtn.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
            false
        }
        
        playBtn.setOnClickListener {
            val selectedSkinId = saveManager.getInt("equipped_skin", 0)
            val colorStr = skinManager.getSkinColor(selectedSkinId)
            gameView.initializeGame(colorStr)
            startGame()
        }

        settingsBtn.setOnClickListener {
            audioManager.isSoundEnabled = !audioManager.isSoundEnabled
            saveManager.saveBoolean("sound_enabled", audioManager.isSoundEnabled)
            updateSettingsBtnText()
        }
        
        missionsBtn.setOnClickListener {
            audioManager.playShopOpen()
            showMissionsDialog()
        }

        leaderboardBtn.setOnClickListener {
            audioManager.playShopOpen()
            showLeaderboardsDialog()
        }

        pauseBtn.setOnClickListener {
            if (gameView.gameState == GameState.PLAYING) {
                gameView.gameState = GameState.PAUSED
                audioManager.pauseBGM()
                showPauseDialog()
            }
        }

        btnTriggerOverdrive.setOnClickListener {
            gameView.triggerManualOverdrive()
            btnTriggerOverdrive.visibility = View.GONE
        }

        shopBtn.setOnClickListener {
            audioManager.playShopOpen()
            showHeroShopDialog()
        }
    }
    
    private fun showMissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_missions, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.85f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 24
            }
        }

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.missions_recycler_view)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = missionManager.missions.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val itemView = layoutInflater.inflate(R.layout.item_mission, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val mission = missionManager.missions[position]
                val itemView = holder.itemView
                
                itemView.findViewById<TextView>(R.id.missionTitle).text = mission.title
                itemView.findViewById<TextView>(R.id.missionDesc).text = mission.desc
                
                itemView.findViewById<ProgressBar>(R.id.missionProgress).apply {
                    max = mission.target
                    progress = mission.progress
                }
                
                val progText = itemView.findViewById<TextView>(R.id.missionProgressText)
                val solvedStamp = itemView.findViewById<View>(R.id.solvedStampContainer)
                val lockedStamp = itemView.findViewById<View>(R.id.lockedStampContainer)
                
                val ratio = if (mission.target > 0) mission.progress.toFloat() / mission.target else 0f
                val barsFilled = (10 * ratio).toInt().coerceIn(0, 10)
                val barsEmpty = 10 - barsFilled
                val barStr = "[ ${"|".repeat(barsFilled)}${"-".repeat(barsEmpty)} ]"
                progText.text = "$barStr  ${mission.progress}/${mission.target}"
                
                val currentIndex = saveManager.getInt("current_linear_mission_index", 0)
                
                if (position > currentIndex) {
                    lockedStamp.visibility = View.VISIBLE
                    solvedStamp.visibility = View.GONE
                    itemView.findViewById<TextView>(R.id.missionTitle).text = "LOCKED"
                    itemView.findViewById<TextView>(R.id.missionDesc).text = "Complete previous mission to unlock"
                    (itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(android.graphics.Color.parseColor("#151B29"))
                    progText.text = "NOT STARTED"
                } else {
                    lockedStamp.visibility = View.GONE
                    if (mission.completed || mission.claimed) {
                        solvedStamp.visibility = View.VISIBLE
                        progText.text = "DONE"
                        (itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFD700"))
                    } else {
                        solvedStamp.visibility = View.GONE
                        (itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(android.graphics.Color.parseColor("#1E2A38"))
                    }
                }
            }
        }
        recyclerView.adapter = adapter
        
        val activeIndex = saveManager.getInt("current_linear_mission_index", 0)
        recyclerView.scrollToPosition(activeIndex)

        // Slide-in Animation from Right
        val rootPanel = view.findViewById<View>(R.id.missions_recycler_view).parent as View
        rootPanel.translationX = 1000f
        rootPanel.animate().translationX(0f).setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .withEndAction { /* Make sure UI thread concludes animation */ }
            .start()

        val closeBtn = view.findViewById<Button>(R.id.btn_close_missions)
        closeBtn.setOnClickListener {
            closeBtn.isEnabled = false
            rootPanel.animate().translationX(1000f).setDuration(250)
                .withEndAction { dialog.dismiss() }
                .start()
        }


        dialog.setOnDismissListener {
            audioManager.stopAmbientSparkle()
        }

        audioManager.startAmbientSparkle()
        dialog.show()
    }

    private fun showHeroShopDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_hero_shop, null)
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(view)
        
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 15
            }
        }

        val btnClose = view.findViewById<ImageButton>(R.id.btn_close_shop)
        val coinsText = view.findViewById<TextView>(R.id.shop_coins_text)
        val activePreview = view.findViewById<android.widget.ImageView>(R.id.active_hero_preview)
        val activeName = view.findViewById<TextView>(R.id.active_hero_name)
        val gridLayout = view.findViewById<android.widget.GridLayout>(R.id.hero_grid)
        val btnBuy = view.findViewById<Button>(R.id.btn_buy_hero)
        val shopPanel = view.findViewById<View>(R.id.shop_panel)
        
        // Entrance animation: Slide up
        shopPanel.translationY = 2000f
        shopPanel.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()

        var selectedSkinIndex = saveManager.getInt("equipped_skin", 0)

        // Make active preview dance
        val scaleYAnim = android.animation.ObjectAnimator.ofFloat(activePreview, "scaleY", 0.9f, 1.15f)
        scaleYAnim.duration = 450
        scaleYAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleYAnim.repeatMode = android.animation.ValueAnimator.REVERSE
        val floatAnim = android.animation.ObjectAnimator.ofFloat(activePreview, "translationY", 0f, -25f)
        floatAnim.duration = 450
        floatAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        floatAnim.repeatMode = android.animation.ValueAnimator.REVERSE
        val animatorSet = android.animation.AnimatorSet()
        animatorSet.playTogether(scaleYAnim, floatAnim)
        animatorSet.start()

        fun closeShop() {
            shopPanel.animate()
                .scaleX(0.7f).scaleY(0.7f).alpha(0f)
                .setDuration(200)
                .withEndAction { dialog.dismiss() }
                .start()
        }

        fun updateDialogUI() {
            coinsText.text = "${currencyManager.getCoins()}"
            val equippedId = saveManager.getInt("equipped_skin", 0)
            val selectedSkin = SkinManager.ALL_SKINS.firstOrNull { it.id == selectedSkinIndex } ?: SkinManager.ALL_SKINS[0]

            activeName.text = selectedSkin.name
            
            // Tint properly based on selected skin
            val drawable = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_player_preview)
            drawable?.let {
                androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
                androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(selectedSkin.hexColor))
                activePreview.background = it
            }

            // Fill Grid
            gridLayout.removeAllViews()
            SkinManager.ALL_SKINS.forEach { skin ->
                val itemView = layoutInflater.inflate(R.layout.item_hero, gridLayout, false)
                val icon = itemView.findViewById<android.widget.ImageView>(R.id.hero_preview_icon)
                val price = itemView.findViewById<TextView>(R.id.hero_price_text)
                val lock = itemView.findViewById<android.widget.ImageView>(R.id.hero_lock_icon)

                // Render Background
                val bgDrawable = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_player_preview)?.mutate()
                bgDrawable?.let {
                    androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
                    androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(skin.hexColor))
                    icon.background = it
                }

                if (skinManager.isSkinUnlocked(skin.id, saveManager)) {
                    lock.visibility = View.GONE
                    price.text = "OWNED"
                    price.setTextColor(Color.parseColor("#4CAF50"))
                    if (equippedId == skin.id) {
                        itemView.setBackgroundResource(R.drawable.bg_hero_equipped)
                    } else {
                        itemView.setBackgroundResource(R.drawable.bg_hero_unlocked)
                    }
                } else {
                    lock.visibility = View.VISIBLE
                    price.text = "${skin.price}"
                    price.setTextColor(Color.WHITE)
                    // Silhouette tint
                    bgDrawable?.let {
                        androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor("#151B29"))
                    }
                }
                
                // Highlight focused item
                if (skin.id == selectedSkinIndex) {
                    itemView.alpha = 1.0f
                    itemView.setBackgroundResource(R.drawable.bg_hero_equipped_neon)
                    val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                        itemView,
                        android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f),
                        android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f)
                    ).apply {
                        duration = 800
                        repeatCount = android.animation.ValueAnimator.INFINITE
                        repeatMode = android.animation.ValueAnimator.REVERSE
                        interpolator = android.view.animation.OvershootInterpolator(2f)
                    }
                    pulse.start()
                    itemView.setTag(R.id.hero_grid, pulse) // Use resource id as safe key
                } else {
                    itemView.alpha = 0.6f
                    itemView.scaleX = 1f
                    itemView.scaleY = 1f
                }

                itemView.setOnClickListener {
                    if (selectedSkinIndex != skin.id) {
                        audioManager.playClick()
                    }
                    selectedSkinIndex = skin.id
                    updateDialogUI()
                }

                gridLayout.addView(itemView)
            }

            // Setup Buy/Equip Button
            if (skinManager.isSkinUnlocked(selectedSkinIndex, saveManager)) {
                if (equippedId == selectedSkinIndex) {
                    btnBuy.text = "SELECTED"
                    btnBuy.isEnabled = false
                    btnBuy.setBackgroundResource(R.drawable.bg_gray_btn)
                } else {
                    btnBuy.text = "SELECT"
                    btnBuy.isEnabled = true
                    btnBuy.setBackgroundResource(R.drawable.bg_cyan_btn)
                }
            } else {
                btnBuy.text = "UNLOCK - ${selectedSkin.price}"
                btnBuy.isEnabled = true
                btnBuy.setBackgroundResource(R.drawable.bg_buy_btn_gold)
            }

            btnBuy.setOnClickListener {
                if (skinManager.isSkinUnlocked(selectedSkinIndex, saveManager)) {
                    saveManager.saveInt("equipped_skin", selectedSkinIndex)
                    audioManager.playClick()
                    updateDialogUI()
                    
                    // sync home screen globally
                    val skinColor = skinManager.getSkinColor(selectedSkinIndex)
                    val homeDrawable = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_player_preview)
                    homeDrawable?.let {
                        androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
                        androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(skinColor))
                        playerPreviewImage.background = it
                    }
                    closeShop()
                } else {
                    if (currencyManager.getCoins() >= selectedSkin.price) {
                        currencyManager.spendCoins(selectedSkin.price)
                        skinManager.unlockSkin(selectedSkinIndex, saveManager)
                        saveManager.saveInt("equipped_skin", selectedSkinIndex)
                        audioManager.playChaChing()
                        updateDialogUI()
                        homeCoinsText.text = " ${currencyManager.getCoins()}"
                        
                        val skinColor = skinManager.getSkinColor(selectedSkinIndex)
                        val homeDrawable = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_player_preview)
                        homeDrawable?.let {
                            androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
                            androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(skinColor))
                            playerPreviewImage.background = it
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Not enough coins!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnClose.setOnClickListener {
            closeShop()
        }

        dialog.setOnDismissListener {
            audioManager.stopAmbientSparkle()
        }

        updateDialogUI()
        audioManager.startAmbientSparkle()
        dialog.show()
    }

    private fun startPlayerDancing() {
        val scaleYAnim = android.animation.ObjectAnimator.ofFloat(playerPreviewImage, "scaleY", 0.9f, 1.1f)
        scaleYAnim.duration = 450
        scaleYAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleYAnim.repeatMode = android.animation.ValueAnimator.REVERSE

        val floatAnim = android.animation.ObjectAnimator.ofFloat(playerPreviewImage, "translationY", 0f, -15f)
        floatAnim.duration = 450
        floatAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        floatAnim.repeatMode = android.animation.ValueAnimator.REVERSE

        val animatorSet = android.animation.AnimatorSet()
        animatorSet.playTogether(scaleYAnim, floatAnim)
        animatorSet.start()
    }

    private fun startGame() {
        homeScreen.visibility = View.GONE
        hudScreen.visibility = View.VISIBLE
        pauseBtn.text = "II"
        hasUsedRevive = false
        gameView.gameState = GameState.PLAYING
        
        // Reset Overdrive State
        
        audioManager.resumeBGM()
        startMissionHud()
    }

    private var missionHudPoller: Runnable? = null

    private fun startMissionHud() {
        val hudCard = findViewById<View>(R.id.hud_mission_card)
        val title = findViewById<TextView>(R.id.hud_mission_title)
        val prog = findViewById<ProgressBar>(R.id.hud_mission_progress)
        
        missionHudPoller?.let { hudCard.removeCallbacks(it) }
        
        val activeMissions = missionManager.missions.filter { !it.completed && !it.claimed }
        if (activeMissions.isEmpty()) {
            hudCard.visibility = View.GONE
            return
        }
        hudCard.visibility = View.VISIBLE
        
        val currentMission = activeMissions.first()
        title.text = currentMission.title
        prog.max = currentMission.target
        prog.progress = currentMission.progress
        
        missionHudPoller = object : Runnable {
            override fun run() {
                if (gameView.gameState == GameState.PLAYING) {
                    prog.progress = currentMission.progress
                    if (currentMission.completed) {
                        hudCard.animate().alpha(0f).setDuration(500).withEndAction { 
                            hudCard.visibility = View.GONE
                            hudCard.alpha = 1f 
                        }.start()
                        return
                    }
                    hudCard.postDelayed(this, 500)
                } else if (gameView.gameState == GameState.PAUSED) {
                    hudCard.postDelayed(this, 500)
                } else {
                    hudCard.visibility = View.GONE
                }
            }
        }
        hudCard.post(missionHudPoller)
    }

    private fun showHomeScreen() {
        gameView.changeClimate()
        gameView.gameState = GameState.HOME
        homeScreen.visibility = View.VISIBLE
        hudScreen.visibility = View.GONE
        
        val coins = currencyManager.getCoins()
        homeCoinsText.text = " $coins"
        audioManager.pauseBGM()

        // Spin the coin
        val homeCoinIcon = findViewById<android.widget.ImageView>(R.id.home_coin_icon)
        if (homeCoinIcon != null) {
            val coinSpin = android.animation.ObjectAnimator.ofFloat(homeCoinIcon, "rotationY", 0f, 360f)
            coinSpin.duration = 2000
            coinSpin.repeatCount = android.animation.ObjectAnimator.INFINITE
            coinSpin.interpolator = android.view.animation.LinearInterpolator()
            coinSpin.start()
        }
        
        // Pulse the play button and logo simultaneously
        val titleText = findViewById<android.widget.TextView>(R.id.title_text)
        
        val playPulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            playBtn,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f)
        ).apply {
            duration = 1200
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
        }
        
        val logoPulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            titleText,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f)
        ).apply {
            duration = 1200
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
        }

        android.animation.AnimatorSet().apply {
            playTogether(playPulse, logoPulse)
            start()
        }


        // Tint the player preview to the currently selected skin
        val skinId = saveManager.getInt("equipped_skin", 0)
        val skinColor = skinManager.getSkinColor(skinId)
        val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_player_preview)
        drawable?.let {
            androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
            androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(skinColor))
            playerPreviewImage.background = it
        }
    }

    private fun showGameOverScreen(score: Int) {
        hudScreen.visibility = View.GONE
        audioManager.playGameOverSequence()
        
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_game_over, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 48
            }
        }

        // Apply Gradient to Title
        val title = view.findViewById<TextView>(R.id.go_title_text)
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, 100f,
            intArrayOf(Color.parseColor("#FF512F"), Color.parseColor("#F09819")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        title.paint.shader = shader

        // Handle Score
        view.findViewById<TextView>(R.id.go_score_text).text = "$score"
        
        val previousHighScore = localHistoryManager.getHighestAltitude()
        
        // Save the run to Local Leaderboards
        val runTime = gameView.getRunTimeSeconds()
        val sessionCoins = gameView.sessionCoins
        val skinId = saveManager.getInt("equipped_skin", 0)
        
        localHistoryManager.insertRun(RunHistoryRecord(
            score = score, 
            coins = sessionCoins, 
            durationSeconds = runTime, 
            timestamp = System.currentTimeMillis(), 
            skinId = skinId, 
            ghostPathJson = gameView.ghostPathJson
        ))
        
        val exactHighScore = localHistoryManager.getHighestAltitude()
        
        val bestText = view.findViewById<TextView>(R.id.go_best_score_text)
        val newBadge = view.findViewById<TextView>(R.id.go_new_best_badge)
        
        if (score > previousHighScore) {
            // Updated high score logic
            bestText.text = "BEST: $exactHighScore"
            newBadge.visibility = View.VISIBLE
            // Pulse badge
            val scaleAnim = android.animation.ObjectAnimator.ofFloat(newBadge, "scaleX", 1f, 1.1f)
            scaleAnim.repeatCount = android.animation.ObjectAnimator.INFINITE
            scaleAnim.repeatMode = android.animation.ObjectAnimator.REVERSE
            scaleAnim.duration = 400
            val scaleAnimY = android.animation.ObjectAnimator.ofFloat(newBadge, "scaleY", 1f, 1.1f)
            scaleAnimY.repeatCount = android.animation.ObjectAnimator.INFINITE
            scaleAnimY.repeatMode = android.animation.ObjectAnimator.REVERSE
            scaleAnimY.duration = 400
            android.animation.AnimatorSet().apply { playTogether(scaleAnim, scaleAnimY); start() }
        } else {
            bestText.text = "BEST: $exactHighScore"
        }

        // Coins
        val earnedCoins = (score / 1000) * 10
        val totalEarned = earnedCoins + sessionCoins
        view.findViewById<TextView>(R.id.go_coins_earned_text).text = "+$totalEarned Coins"

        // Coin spin animation
        val coinIcon = view.findViewById<android.widget.ImageView>(R.id.go_coin_icon)
        android.animation.ObjectAnimator.ofFloat(coinIcon, "rotationY", 0f, 360f).apply {
            duration = 1500
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }

        // Buttons
        val btnRevive = view.findViewById<Button>(R.id.btn_revive)
        if (hasUsedRevive) {
            btnRevive.isEnabled = false
            btnRevive.text = "MAX REACHED"
            btnRevive.alpha = 0.5f // Grayscale ghosting
            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            btnRevive.background.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        } else {
            btnRevive.visibility = View.GONE
        }

        val btnRetry = view.findViewById<Button>(R.id.btn_retry)
        btnRetry.setOnClickListener {
            dialog.dismiss()
            audioManager.pauseGameOverSequence()
            val selectedSkinId = saveManager.getInt("equipped_skin", 0)
            gameView.initializeGame(skinManager.getSkinColor(selectedSkinId))
            startGame()
        }

        // Slow pulse animation 
        val retryScaleX = android.animation.ObjectAnimator.ofFloat(btnRetry, "scaleX", 1f, 1.05f)
        retryScaleX.repeatCount = android.animation.ObjectAnimator.INFINITE
        retryScaleX.repeatMode = android.animation.ObjectAnimator.REVERSE
        retryScaleX.duration = 800
        val retryScaleY = android.animation.ObjectAnimator.ofFloat(btnRetry, "scaleY", 1f, 1.05f)
        retryScaleY.repeatCount = android.animation.ObjectAnimator.INFINITE
        retryScaleY.repeatMode = android.animation.ObjectAnimator.REVERSE
        retryScaleY.duration = 800
        android.animation.AnimatorSet().apply { playTogether(retryScaleX, retryScaleY); start() }

        view.findViewById<ImageButton>(R.id.btn_go_home).setOnClickListener {
            dialog.dismiss()
            audioManager.pauseGameOverSequence()
            showHomeScreen()
        }

        view.findViewById<ImageButton>(R.id.btn_share).setOnClickListener {
            val sendIntent: android.content.Intent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, "I just scored $score in Scooby Jump! Can you beat it?")
                type = "text/plain"
            }
            startActivity(android.content.Intent.createChooser(sendIntent, "Share Score"))
        }

        dialog.show()

        // Bouncy Entrance Animation
        val panel = view.findViewById<View>(R.id.game_over_panel)
        panel.translationY = -2500f
        
        val endAction = Runnable {
            if (score > previousHighScore && score > 500 && !saveManager.getBoolean("has_rated", false)) {
                view.postDelayed({ showReviewDialog() }, 1000)
            }
        }
        
        panel.animate()
            .translationY(0f)
            .setDuration(900)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .withEndAction(endAction)
            .start()
    }

    private fun showReviewDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_review, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 48
            }
        }
        
        val panel = view.findViewById<View>(R.id.review_panel)
        panel.scaleX = 0f
        panel.scaleY = 0f
        panel.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
        
        view.findViewById<Button>(R.id.btn_review_sure).setOnClickListener {
            saveManager.saveBoolean("has_rated", true)
            audioManager.playChaChing()
            dialog.dismiss()
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
        
        view.findViewById<Button>(R.id.btn_review_later).setOnClickListener {
            saveManager.saveBoolean("has_rated", false)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun updateSettingsBtnText() {
        val iconRes = if (audioManager.isSoundEnabled) R.drawable.ic_speaker_neon else R.drawable.ic_speaker_muted_neon
        settingsBtn.setImageResource(iconRes)
    }

    private fun showPauseDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_pause, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(true) // Allow hardware back button to resume

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.85f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 24
            }
        }
        
        dialog.setOnCancelListener {
            if (!isCountdownActive) {
                startUnpauseCountdown()
            }
        }

        view.findViewById<Button>(R.id.btn_resume).setOnClickListener {
            if (!isCountdownActive) {
                dialog.dismiss()
                startUnpauseCountdown()
            }
        }

        view.findViewById<Button>(R.id.btn_exit).setOnClickListener {
            dialog.dismiss()
            showHomeScreen()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (gameView.gameState == GameState.PLAYING) audioManager.resumeBGM()
    }

    override fun onPause() {
        super.onPause()
        audioManager.pauseBGM()
        audioManager.pauseGameOverSequence()
        if (gameView.gameState == GameState.PLAYING) {
            gameView.gameState = GameState.PAUSED
            showPauseDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        analyticsManager.logSessionEnd()
    }

    private fun showReviveCountdownDialog(finalScore: Int) {
        gameView.safeguardPlayer() // Freeze physics and place on safe platform instantly
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_revive, null)
        dialog.setContentView(view)
        dialog.setCancelable(true) // Allow back button to cancel

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 48
            }
        }
        
        var timer: android.os.CountDownTimer? = null
        
        // Handle physical back button explicitly
        dialog.setOnCancelListener {
            timer?.cancel()
            showGameOverScreen(finalScore)
        }
        
        val timerProgress = view.findViewById<ProgressBar>(R.id.revive_progress)
        val timerText = view.findViewById<TextView>(R.id.revive_countdown_text)
        val watchAdBtn = view.findViewById<Button>(R.id.btn_watch_ad_revive)

        if (!adManager.isRewardedAdReady()) {
            watchAdBtn.isEnabled = false
            watchAdBtn.text = "AD UNAVAILABLE"
            watchAdBtn.setBackgroundResource(R.drawable.bg_gray_btn)
        } else {
            watchAdBtn.text = "GHOST SPRINT (10s)"
        }
        
        watchAdBtn.setOnClickListener {
            timer?.cancel()
            dialog.setCancelable(false) // Prevent cancelling while ad loads
            Toast.makeText(this, "Loading Revive Ad...", Toast.LENGTH_SHORT).show()
            
            findViewById<View>(R.id.ad_loading_overlay).visibility = View.VISIBLE
            gameView.gameState = GameState.AD_PLAYING
            gameView.suspendMainLoop()
            
            adManager.showRewardedAd { rewarded ->
                lifecycleScope.launch(Dispatchers.Main) {
                    findViewById<View>(R.id.ad_loading_overlay).visibility = View.GONE
                    if (rewarded) {
                        dialog.dismiss()
                        hasUsedRevive = true
                        
                        // Critical Fix: Wait for AdMob's Activity transition to fully complete & yield Window Focus 
                        // back to the MainActivity Choreographer before firing the UI-heavy 3-2-1 loop.
                        delay(400)
                        handleReviveSuccess(finalScore)
                    } else {
                        Toast.makeText(this@MainActivity, "Ad failed or closed.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        
                        delay(150)
                        showGameOverScreen(finalScore)
                    }
                }
            }
        }
        
        dialog.show()
        audioManager.pauseBGM()
        
        // 5 Second countdown
        timer = object : android.os.CountDownTimer(5000, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = Math.ceil(millisUntilFinished / 1000.0).toInt()
                timerText.text = "$secondsRemaining"
                timerProgress.progress = millisUntilFinished.toInt()
            }
            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss()
                    showGameOverScreen(finalScore)
                }
            }
        }.start()
        
        // Bouncy Entrance
        val panel = view.findViewById<View>(R.id.revive_panel)
        panel.translationY = -2500f
        panel.animate().translationY(0f).setDuration(900).setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
    }

    private fun handleReviveSuccess(finalScore: Int) {
        if (isCountdownActive) return
        isCountdownActive = true

        val overlay = findViewById<TextView>(R.id.tv_countdown_overlay)
        overlay.visibility = View.VISIBLE
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            findViewById<View>(R.id.game_view_container).setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.MIRROR)
            )
        }
        
        // Ensure player is definitely locked directly on golden platform coordinates
        gameView.safeguardPlayer()
        gameView.gameState = GameState.PAUSED
        
        // Critical! Resume the thread BEFORE Countdown passes so Canvas draws Scooby hovering!
        gameView.resumeMainLoop()
        gameView.requestFocus()
        
        var count = 3
        overlay.text = "$count"
        audioManager.playTick()
        overlay.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        
        fun animatePop() {
            overlay.scaleX = 0.4f
            overlay.scaleY = 0.4f
            overlay.alpha = 0f
            overlay.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                .start()
        }
        
        animatePop()
        
        object : android.os.CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val displayCount = Math.ceil(millisUntilFinished / 1000.0).toInt()
                
                if (displayCount == 0 || displayCount == 1 && millisUntilFinished < 150) {
                    overlay.text = "GO!"
                    audioManager.playDing()
                    overlay.setTextColor(Color.parseColor("#39FF14"))
                    overlay.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } else {
                    val actualDisplay = if (displayCount > 3) 3 else displayCount
                    overlay.text = "$actualDisplay"
                    audioManager.playTick()
                    overlay.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                animatePop()
            }
            
            override fun onFinish() {
                isCountdownActive = false
                overlay.visibility = View.GONE
                overlay.setTextColor(Color.WHITE)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    findViewById<View>(R.id.game_view_container).setRenderEffect(null)
                }
                hudScreen.visibility = View.VISIBLE
                audioManager.resumeBGM()
                
                gameView.executeRevive() // Unfreeze physics!
            }
        }.start()
    }

    private fun startUnpauseCountdown() {
        if (isCountdownActive) return
        isCountdownActive = true

        val overlay = findViewById<TextView>(R.id.tv_countdown_overlay)
        overlay.visibility = View.VISIBLE
        
        var count = 3
        overlay.text = "$count"
        audioManager.playTick()
        
        fun animatePop() {
            overlay.scaleX = 0.4f
            overlay.scaleY = 0.4f
            overlay.alpha = 0f
            overlay.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                .start()
        }
        animatePop()
        
        object : android.os.CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val displayCount = Math.ceil(millisUntilFinished / 1000.0).toInt()
                overlay.text = if (displayCount == 0) "JUMP!" else "$displayCount"
                animatePop()
                
                if (displayCount == 0) {
                    audioManager.playDing()
                    overlay.setTextColor(Color.parseColor("#39FF14"))
                } else if (displayCount < 3) {
                    audioManager.playTick()
                    overlay.setTextColor(Color.WHITE)
                }
            }
            override fun onFinish() {
                isCountdownActive = false
                overlay.visibility = View.GONE
                overlay.setTextColor(Color.WHITE)
                hudScreen.visibility = View.VISIBLE
                audioManager.resumeBGM()
                gameView.gameState = GameState.PLAYING
            }
        }.start()
    }

    private fun showConfirmExitDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_exit, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.85f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 40
            }
        }
        
        view.findViewById<Button>(R.id.btn_stay).setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<Button>(R.id.btn_quit).setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }
        
        dialog.show()
    }

    private fun showLeaderboardsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_leaderboard, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.85f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes?.blurBehindRadius = 24
            }
        }

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.leaderboard_recycler_view)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val records = localHistoryManager.getTopRuns(20)

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = records.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val itemView = layoutInflater.inflate(R.layout.item_leaderboard, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val record = records[position]
                val itemView = holder.itemView
                
                val rankText = itemView.findViewById<TextView>(R.id.lb_rank)
                val heroIcon = itemView.findViewById<View>(R.id.lb_hero_icon)
                val scoreStatsText = itemView.findViewById<TextView>(R.id.lb_score_stats_text)
                
                rankText.text = "#${position + 1}"
                
                // Color rank string based on top 3
                when(position) {
                    0 -> rankText.setTextColor(Color.parseColor("#FFD700")) // Gold
                    1 -> rankText.setTextColor(Color.parseColor("#C0C0C0")) // Silver
                    2 -> rankText.setTextColor(Color.parseColor("#CD7F32")) // Bronze
                    else -> rankText.setTextColor(Color.WHITE)
                }

                val mins = record.durationSeconds / 60
                val secs = record.durationSeconds % 60
                
                scoreStatsText.text = "${record.score}m  |  ${record.coins} Coins  |  ${String.format("%02d:%02d", mins, secs)}"
                
                val skinColor = skinManager.getSkinColor(record.skinId)
                val drawable = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_player_preview)?.mutate()
                drawable?.let {
                    androidx.core.graphics.drawable.DrawableCompat.setTintMode(it, android.graphics.PorterDuff.Mode.MULTIPLY)
                    androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.parseColor(skinColor))
                    heroIcon.background = it
                }
            }
        }
        recyclerView.adapter = adapter

        // Entrance animation
        val rootPanel = view.findViewById<View>(R.id.leaderboard_recycler_view).parent as View
        rootPanel.translationY = 1000f
        rootPanel.animate().translationY(0f).setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()

        view.findViewById<Button>(R.id.btn_close_leaderboard).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            audioManager.stopAmbientSparkle()
        }

        audioManager.startAmbientSparkle()
        dialog.show()
    }
}
