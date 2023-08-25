package com.eidogs.pics

import android.app.Application

class PhotoApp: Application() {
    override fun onCreate() {
        super.onCreate()
        photoApp = this
    }

    companion object {
        var photoApp: PhotoApp? = null
        private set
        val TAG = PhotoApp::class.java.simpleName
    }
}