package com.watchyou.util

import timber.log.Timber

object Logger {
    fun d(tag: String, msg: String) { Timber.tag(tag).d(msg) }
    fun i(tag: String, msg: String) { Timber.tag(tag).i(msg) }
    fun w(tag: String, msg: String) { Timber.tag(tag).w(msg) }
    fun e(tag: String, msg: String) { Timber.tag(tag).e(msg) }
    fun e(tag: String, msg: String, throwable: Throwable) { Timber.tag(tag).e(throwable, msg) }
}
