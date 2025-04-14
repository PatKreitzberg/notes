package com.wyldsoft.notes

import android.app.Application
import android.content.Context
import org.lsposed.hiddenapibypass.HiddenApiBypass


class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        checkHiddenApiBypass()
        // Initialize any required components here
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}