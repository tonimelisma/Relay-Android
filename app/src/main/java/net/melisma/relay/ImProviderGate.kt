package net.melisma.relay

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide gate for Samsung RCS proprietary provider (authority: "im").
 * - Detects once per install and persists the result in SharedPreferences
 * - Call [prime] early (e.g., in Application.onCreate)
 * - Use [shouldUseIm] to decide whether to register observers or query the provider
 * - Call [markUnavailable] if a later runtime failure indicates it should be disabled permanently
 */
object ImProviderGate {
    private const val PREFS_NAME: String = "app_meta"
    private const val KEY_CHECKED: String = "imProviderChecked"
    private const val KEY_AVAILABLE: String = "imProviderAvailable"

    private val cachedAvailability: AtomicReference<Boolean?> = AtomicReference(null)

    fun prime(context: Context) {
        // If already cached, skip
        if (cachedAvailability.get() != null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val checked = prefs.getBoolean(KEY_CHECKED, false)
        if (checked) {
            cachedAvailability.set(prefs.getBoolean(KEY_AVAILABLE, false))
            return
        }

        val available = detectAvailability(context)
        cachedAvailability.set(available)
        prefs.edit().putBoolean(KEY_CHECKED, true).putBoolean(KEY_AVAILABLE, available).apply()
    }

    fun shouldUseIm(context: Context): Boolean {
        val cached = cachedAvailability.get()
        return if (cached != null) cached else {
            prime(context)
            cachedAvailability.get() == true
        }
    }

    fun shouldUseImOrCachedFalse(): Boolean {
        return cachedAvailability.get() == true
    }

    fun markUnavailable(context: Context) {
        cachedAvailability.set(false)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CHECKED, true).putBoolean(KEY_AVAILABLE, false).apply()
    }

    private fun detectAvailability(context: Context): Boolean {
        return try {
            val pm: PackageManager = context.packageManager
            val provider = pm.resolveContentProvider("im", 0)
            if (provider == null) return false
            // Light probe: best-effort query with limit; any success implies presence
            val uri = Uri.parse("content://im/chat").buildUpon().appendQueryParameter("limit", "1").build()
            val cr: ContentResolver = context.contentResolver
            cr.query(uri, null, null, null, null)?.use { /* success if no exception */ }
            true
        } catch (_: Throwable) {
            false
        }
    }
}


