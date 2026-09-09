package io.github.dhianapereira.afazeres.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
private val Context.store by preferencesDataStore("preferences")
class Preferences(private val context: Context) {
    private val automaticKey = booleanPreferencesKey("automatic_classification")
    val automaticClassification = context.store.data.map { it[automaticKey] ?: true }
    suspend fun automaticClassification(value: Boolean) { context.store.edit { it[automaticKey] = value } }
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.store.data.map { it[themeKey] ?: "system" }
    suspend fun theme(value: String) { context.store.edit { it[themeKey] = value } }
}
