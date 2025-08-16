package net.melisma.relay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val DEFAULT_TAG: String = "Relay"
    private const val MAX_FILES: Int = 7
    private const val MAX_FILE_BYTES: Long = 1_000_000 // ~1MB per file
    private const val LOG_FILE_PREFIX: String = "relay-"
    private const val LOG_FILE_SUFFIX: String = ".log"

    private var logDir: File? = null
    private var currentFile: File? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        try {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            logDir = dir
            rotateIfNeeded()
            i("AppLogger initialized. dir=${dir.absolutePath}", tag = "AppLogger")
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun d(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            println("D/$tag: $message")
        }
        writeToFile("D", tag, message)
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.i(tag, message)
        } catch (_: Throwable) {
            println("I/$tag: $message")
        }
        writeToFile("I", tag, message)
    }

    fun w(message: String, tag: String = DEFAULT_TAG) {
        try {
            Log.w(tag, message)
        } catch (_: Throwable) {
            println("W/$tag: $message")
        }
        writeToFile("W", tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        try {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        } catch (_: Throwable) {
            if (throwable != null) println("E/$tag: $message | ${throwable.message}") else println("E/$tag: $message")
        }
        val full = if (throwable != null) "$message | ${throwable.stackTraceToString()}" else message
        writeToFile("E", tag, full)
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        val dir = logDir ?: return
        ioScope.launch {
            try {
                rotateIfNeeded()
                val file = currentFile ?: newLogFile()
                val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
                file.appendText("$ts $level/$tag: $message\n")
            } catch (_: Throwable) { }
        }
    }

    @Synchronized
    private fun rotateIfNeeded() {
        val dir = logDir ?: return
        val file = currentFile ?: newLogFile()
        if (file.length() >= MAX_FILE_BYTES) {
            currentFile = newLogFile()
        }
        val files = dir.listFiles { f -> f.name.startsWith(LOG_FILE_PREFIX) && f.name.endsWith(LOG_FILE_SUFFIX) }
            ?.sortedBy { it.lastModified() }
            ?: return
        if (files.size > MAX_FILES) {
            files.take(files.size - MAX_FILES).forEach { it.delete() }
        }
    }

    private fun newLogFile(): File {
        val dir = logDir ?: throw IllegalStateException("AppLogger not initialized")
        val name = LOG_FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + LOG_FILE_SUFFIX
        return File(dir, name).also { currentFile = it }
    }
}

