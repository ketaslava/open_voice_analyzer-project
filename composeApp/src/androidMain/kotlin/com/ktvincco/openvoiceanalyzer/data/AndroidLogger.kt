package com.ktvincco.openvoiceanalyzer.data

import android.util.Log


class AndroidLogger: Logger {

    override fun log(logTag: String, message: String) {
        Log.i(logTag, message)
    }

    override fun logW(logTag: String, message: String) {
        Log.w(logTag, message)
    }

    override fun logE(logTag: String, message: String) {
        Log.e(logTag, message)
    }
}