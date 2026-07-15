package com.doorbell.detector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doorbell_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val API_URL = stringPreferencesKey("api_url")
        private val SELECTED_PACKAGES = stringPreferencesKey("selected_packages_json")
        private val SELECTED_APP_NAMES = stringPreferencesKey("selected_app_names_json")
        private val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        private val SERVICE_ID = stringPreferencesKey("doorbell_service_id")
    }

    val apiUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_URL] ?: "http://192.168.1.100:3000"
    }

    val doorbellServiceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVICE_ID] ?: ""
    }

    val selectedPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[SELECTED_PACKAGES] ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    val selectedAppNames: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[SELECTED_APP_NAMES] ?: "{}"
        val obj = JSONObject(raw)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    val privacyAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PRIVACY_ACCEPTED] ?: false
    }

    suspend fun setApiUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[API_URL] = url
        }
    }

    suspend fun addSelectedPackage(packageName: String, appName: String) {
        context.dataStore.edit { prefs ->
            val packagesRaw = prefs[SELECTED_PACKAGES] ?: "[]"
            val namesRaw = prefs[SELECTED_APP_NAMES] ?: "{}"
            val packagesArr = JSONArray(packagesRaw)
            val namesObj = JSONObject(namesRaw)

            val existing = (0 until packagesArr.length()).map { packagesArr.getString(it) }.toSet()
            if (packageName !in existing) {
                packagesArr.put(packageName)
            }
            namesObj.put(packageName, appName)

            prefs[SELECTED_PACKAGES] = packagesArr.toString()
            prefs[SELECTED_APP_NAMES] = namesObj.toString()
        }
    }

    suspend fun removeSelectedPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val packagesRaw = prefs[SELECTED_PACKAGES] ?: "[]"
            val namesRaw = prefs[SELECTED_APP_NAMES] ?: "{}"
            val packagesArr = JSONArray(packagesRaw)
            val namesObj = JSONObject(namesRaw)

            val newArr = JSONArray()
            for (i in 0 until packagesArr.length()) {
                val pkg = packagesArr.getString(i)
                if (pkg != packageName) newArr.put(pkg)
            }
            namesObj.remove(packageName)

            prefs[SELECTED_PACKAGES] = newArr.toString()
            prefs[SELECTED_APP_NAMES] = namesObj.toString()
        }
    }

    suspend fun clearSelectedPackages() {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_PACKAGES] = "[]"
            prefs[SELECTED_APP_NAMES] = "{}"
        }
    }

    suspend fun acceptPrivacy() {
        context.dataStore.edit { prefs ->
            prefs[PRIVACY_ACCEPTED] = true
        }
    }

    suspend fun setDoorbellServiceId(serviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVICE_ID] = serviceId
        }
    }
}
