package com.game.scoobyjump

class CurrencyManager(private val saveManager: SaveManager) {
    private val COINS_KEY = "total_coins"

    fun getCoins(): Int = saveManager.getInt(COINS_KEY, 0)
    
    fun addCoins(amount: Int) {
        saveManager.saveInt(COINS_KEY, getCoins() + amount)
    }

    fun spendCoins(amount: Int): Boolean {
        val current = getCoins()
        if (current >= amount) {
            saveManager.saveInt(COINS_KEY, current - amount)
            return true
        }
        return false
    }
}
