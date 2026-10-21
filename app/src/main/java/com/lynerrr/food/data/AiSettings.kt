package com.lynerrr.food.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's Anthropic API key for AI photo estimates. Stored in the app's private preferences: readable only by
 * this app (and by anyone with root or a debug build's run-as), which is fine for a personal app.
 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val _apiKey = MutableStateFlow(prefs.getString(KEY, null).orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setApiKey(value: String) {
        val key = value.trim()
        prefs.edit().putString(KEY, key).apply()
        _apiKey.value = key
    }

    private companion object {
        const val KEY = "anthropic_api_key"
    }
}
