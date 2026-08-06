package com.titaniumharmonics.bad.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import com.titaniumharmonics.bad.audio.matching.JudgementConfigurationStore

@SuppressLint("UseKtx")
class SharedPreferencesJudgementConfigurationStore(context: Context) :
    JudgementConfigurationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "judgement_configuration",
        Context.MODE_PRIVATE,
    )

    override fun load(): Map<String, *> = preferences.all

    override fun save(values: Map<String, Any>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported judgement setting value for $key.")
            }
        }
        check(editor.commit()) { "Unable to persist judgement settings." }
    }

    override fun reset() {
        check(preferences.edit().clear().commit()) { "Unable to reset judgement settings." }
    }
}
