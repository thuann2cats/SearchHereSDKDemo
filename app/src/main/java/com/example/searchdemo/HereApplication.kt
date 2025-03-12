package com.example.searchdemo

import android.app.Application
import android.util.Log
import com.here.sdk.core.engine.AuthenticationMode
import com.here.sdk.core.engine.SDKNativeEngine
import com.here.sdk.core.engine.SDKOptions
import com.here.sdk.core.errors.InstantiationErrorException

class HereApplication : Application() {
    companion object {
        private const val TAG = "HereApplication"
    }

    override fun onCreate() {
        super.onCreate()
        initializeHereSDK()
    }

    private fun initializeHereSDK() {
        val authenticationMode = AuthenticationMode.withKeySecret(
            BuildConfig.HERE_ACCESS_KEY_ID,
            BuildConfig.HERE_ACCESS_KEY_SECRET
        )
        val options = SDKOptions(authenticationMode)
        
        try {
            SDKNativeEngine.makeSharedInstance(this, options)
        } catch (e: InstantiationErrorException) {
            Log.e(TAG, "Failed to initialize HERE SDK: ${e.error.name}")
            throw RuntimeException("Failed to initialize HERE SDK: ${e.error.name}")
        }
    }

    override fun onTerminate() {
        disposeHereSDK()
        super.onTerminate()
    }

    private fun disposeHereSDK() {
        SDKNativeEngine.getSharedInstance()?.let { engine ->
            engine.dispose()
            SDKNativeEngine.setSharedInstance(null)
        }
    }
}
