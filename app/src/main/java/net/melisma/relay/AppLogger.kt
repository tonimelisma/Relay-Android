package net.melisma.relay

import android.util.Log

object AppLogger {
    private const val DEFAULT_TAG: String = "Relay"

    fun d(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.d(tag, message)
        } catch (t: Throwable) {
            // Unit tests without Android runtime
            println("D/$tag: $message")
        }
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.i(tag, message)
        } catch (t: Throwable) {
            println("I/$tag: $message")
        }
    }

    fun w(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.w(tag, message)
        } catch (t: Throwable) {
            println("W/$tag: $message")
        }
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (t: Throwable) {
            if (throwable != null) {
                println("E/$tag: $message | ${throwable.message}")
            } else {
                println("E/$tag: $message")
            }
        }
    }
}


