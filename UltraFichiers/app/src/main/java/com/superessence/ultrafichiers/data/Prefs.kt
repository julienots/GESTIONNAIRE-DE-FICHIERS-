package com.superessence.ultrafichiers.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("ultra", Context.MODE_PRIVATE)

    var autoClean: Boolean
        get() = p.getBoolean("autoClean", false); set(v) = p.edit().putBoolean("autoClean", v).apply()
    var autoDuplicates: Boolean
        get() = p.getBoolean("autoDup", true); set(v) = p.edit().putBoolean("autoDup", v).apply()
    var autoJunk: Boolean
        get() = p.getBoolean("autoJunk", true); set(v) = p.edit().putBoolean("autoJunk", v).apply()
    var autoApks: Boolean
        get() = p.getBoolean("autoApks", true); set(v) = p.edit().putBoolean("autoApks", v).apply()
    var useTrash: Boolean
        get() = p.getBoolean("useTrash", true); set(v) = p.edit().putBoolean("useTrash", v).apply()
    var trashDays: Int
        get() = p.getInt("trashDays", 7); set(v) = p.edit().putInt("trashDays", v).apply()
    var showHidden: Boolean
        get() = p.getBoolean("hidden", false); set(v) = p.edit().putBoolean("hidden", v).apply()
    var sort: Int
        get() = p.getInt("sort", 0); set(v) = p.edit().putInt("sort", v).apply()
    var lastAutoClean: Long
        get() = p.getLong("lastAuto", 0); set(v) = p.edit().putLong("lastAuto", v).apply()
    var totalFreed: Long
        get() = p.getLong("freed", 0); set(v) = p.edit().putLong("freed", v).apply()

    // --- Vidéos ---
    var magicFolder: String
        get() = p.getString("magic", null) ?: File(Util.root, "Ultra Fichiers/Optimiser").absolutePath
        set(v) = p.edit().putString("magic", v).apply()
    var magicEnabled: Boolean
        get() = p.getBoolean("magicOn", true); set(v) = p.edit().putBoolean("magicOn", v).apply()
    var vCompress: Boolean
        get() = p.getBoolean("vCompress", true); set(v) = p.edit().putBoolean("vCompress", v).apply()
    var vSilence: Boolean
        get() = p.getBoolean("vSilence", true); set(v) = p.edit().putBoolean("vSilence", v).apply()
    /** 0 = doux, 1 = normal, 2 = agressif */
    var vSensitivity: Int
        get() = p.getInt("vSens", 1); set(v) = p.edit().putInt("vSens", v).apply()
    var vKeepOriginal: Boolean
        get() = p.getBoolean("vKeep", true); set(v) = p.edit().putBoolean("vKeep", v).apply()

    fun isProcessed(f: File) = p.getStringSet("done", emptySet())!!.contains(key(f))
    fun markProcessed(f: File) {
        val s = p.getStringSet("done", emptySet())!!.toMutableSet()
        s.add(key(f))
        if (s.size > 3000) s.remove(s.first())
        p.edit().putStringSet("done", s).apply()
    }
    private fun key(f: File) = f.absolutePath + "|" + f.length()
}
