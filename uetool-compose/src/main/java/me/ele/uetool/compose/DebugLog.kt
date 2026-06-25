package me.ele.uetool.compose

import android.util.Log

object DebugLog {

    private const val LOG_SKIPPED = 0

    @Volatile
    @JvmStatic
    var enabled: Boolean = false

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean {
        return enabled && Log.isLoggable(tag, level)
    }

    @JvmStatic
    fun v(tag: String, msg: String): Int = logIfEnabled { Log.v(tag, msg) }

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.v(tag, msg, tr) }

    @JvmStatic
    fun v(tag: String, message: () -> String): Int = logIfEnabled { Log.v(tag, message()) }

    @JvmStatic
    fun v(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.v(tag, message(), tr) }
    }

    @JvmStatic
    fun d(tag: String, msg: String): Int = logIfEnabled { Log.d(tag, msg) }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.d(tag, msg, tr) }

    @JvmStatic
    fun d(tag: String, message: () -> String): Int = logIfEnabled { Log.d(tag, message()) }

    @JvmStatic
    fun d(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.d(tag, message(), tr) }
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int = logIfEnabled { Log.i(tag, msg) }

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.i(tag, msg, tr) }

    @JvmStatic
    fun i(tag: String, message: () -> String): Int = logIfEnabled { Log.i(tag, message()) }

    @JvmStatic
    fun i(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.i(tag, message(), tr) }
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int = logIfEnabled { Log.w(tag, msg) }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.w(tag, msg, tr) }

    @JvmStatic
    fun w(tag: String, tr: Throwable): Int = logIfEnabled { Log.w(tag, tr) }

    @JvmStatic
    fun w(tag: String, message: () -> String): Int = logIfEnabled { Log.w(tag, message()) }

    @JvmStatic
    fun w(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.w(tag, message(), tr) }
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int = logIfEnabled { Log.e(tag, msg) }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.e(tag, msg, tr) }

    @JvmStatic
    fun e(tag: String, message: () -> String): Int = logIfEnabled { Log.e(tag, message()) }

    @JvmStatic
    fun e(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.e(tag, message(), tr) }
    }

    @JvmStatic
    fun wtf(tag: String, msg: String): Int = logIfEnabled { Log.wtf(tag, msg) }

    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable?): Int = logIfEnabled { Log.wtf(tag, msg, tr) }

    @JvmStatic
    fun wtf(tag: String, tr: Throwable): Int = logIfEnabled { Log.wtf(tag, tr) }

    @JvmStatic
    fun wtf(tag: String, message: () -> String): Int = logIfEnabled { Log.wtf(tag, message()) }

    @JvmStatic
    fun wtf(tag: String, tr: Throwable?, message: () -> String): Int {
        return logIfEnabled { Log.wtf(tag, message(), tr) }
    }

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int {
        return logIfEnabled { Log.println(priority, tag, msg) }
    }

    @JvmStatic
    fun println(priority: Int, tag: String, message: () -> String): Int {
        return logIfEnabled { Log.println(priority, tag, message()) }
    }

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String = Log.getStackTraceString(tr)

    private inline fun logIfEnabled(block: () -> Int): Int {
        return if (enabled) block() else LOG_SKIPPED
    }
}
