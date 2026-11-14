package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vibereel_settings")

enum class ThemeMode(val labelTr: String) {
    SYSTEM("Sistem"),
    DARK("Karanlık"),
    LIGHT("Aydınlık")
}

class ThemePreferencesRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val githubRepoKey = stringPreferencesKey("github_update_repo")
    private val dismissedUpdateTagKey = stringPreferencesKey("dismissed_update_tag")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[themeKey]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    val githubRepo: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[githubRepoKey].orEmpty()
    }

    val dismissedUpdateTag: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[dismissedUpdateTagKey].orEmpty()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setGithubRepo(repoSlug: String) {
        context.dataStore.edit { prefs ->
            prefs[githubRepoKey] = repoSlug.trim()
        }
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        context.dataStore.edit { prefs ->
            prefs[dismissedUpdateTagKey] = tag.trim()
        }
    }
}
