package com.game.scoobyjump

data class Skin(val id: Int, val name: String, val hexColor: String, val price: Int)

class SkinManager {
    companion object {
        val ALL_SKINS = listOf(
            Skin(0, "Classic Orange", "#FF9800", 0),
            Skin(1, "Golden Edge", "#FFD700", 500),
            Skin(2, "Neon Green", "#00E676", 1000),
            Skin(3, "Electric Purple", "#9C27B0", 1500),
            Skin(4, "Ruby Red", "#E91E63", 2000),
            Skin(5, "Cyber Blue", "#00BFFF", 2500),
            Skin(6, "Diamond White", "#FFFFFF", 3000),
            Skin(7, "Shadow Black", "#121212", 4000),
            Skin(8, "Emerald King", "#50C878", 5000),
            Skin(9, "Cosmic Pink", "#FF69B4", 7500),
            Skin(10, "Galaxy Teal", "#008080", 10000),
            Skin(11, "Sunset Gold", "#FF4500", 15000),
            Skin(12, "Chrome Edition", "#C0C0C0", 99999) // Special Streak Skin
        )
    }

    fun isSkinUnlocked(skinId: Int, saveManager: SaveManager): Boolean {
        if (skinId == 0) return true
        return saveManager.getBoolean("skin_unlocked_${skinId}", false)
    }
    
    fun unlockSkin(skinId: Int, saveManager: SaveManager) {
        saveManager.saveBoolean("skin_unlocked_${skinId}", true)
    }

    fun getSkinColor(skinId: Int): String {
        return ALL_SKINS.find { it.id == skinId }?.hexColor ?: ALL_SKINS[0].hexColor
    }
}
