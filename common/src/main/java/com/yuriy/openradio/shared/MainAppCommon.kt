/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy [chernyshov.yuriy@gmail.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.coradio.shared

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.google.android.gms.security.ProviderInstaller
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.model.storage.AppPreferencesManager
import app.coradio.shared.utils.AnalyticsUtils
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.net.ssl.SSLContext

/**
 * Created with Android Studio.
 * User: Yuriy Chernyshov
 * Date: 12/21/13
 * Time: 6:29 PM
 */
open class MainAppCommon : MultiDexApplication() {

    private val mAppJob = SupervisorJob()
    protected val mAppScope = CoroutineScope(Dispatchers.Main + mAppJob)

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(applicationContext)
        DependencyRegistryCommon.init(applicationContext)
        // Address devices API 19 and lower.
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            sslContext.createSSLEngine()
        } catch (e: Throwable) {
            AppLogger.e("$CLASS_NAME can't install the provider", e)
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        sendStats(applicationContext)

        mAppScope.launch(Dispatchers.IO) {
            correctBufferSettings(applicationContext)
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = MainAppCommon::class.java.simpleName

        /**
         * Send stats of the device to the cloud.
         */
        private fun sendStats(context: Context) {
            AnalyticsUtils.logMessage("OS ver: " + Build.VERSION.RELEASE)
            AnalyticsUtils.logMessage("SDK ver: " + Build.VERSION.SDK_INT)
            AnalyticsUtils.logMessage("Density: : " + AppUtils.getDensityDpi(context))
        }

        /**
         * Correct mal formatted values entered by user.
         *
         * @param context Context of a callee.
         */
        @UnstableApi
        private fun correctBufferSettings(context: Context) {
            val maxBufferMs = AppPreferencesManager.getMaxBuffer(context)
            val minBufferMs = AppPreferencesManager.getMinBuffer(context)
            val playBufferMs = AppPreferencesManager.getPlayBuffer(context)
            val playBufferRebufferMs = AppPreferencesManager.getPlayBufferRebuffer(context)
            if (maxBufferMs < minBufferMs) {
                AppPreferencesManager.setMaxBuffer(context, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS)
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
            if (minBufferMs < playBufferMs) {
                AppPreferencesManager.setPlayBuffer(
                    context, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
                )
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
            if (minBufferMs < playBufferRebufferMs) {
                AppPreferencesManager.setPlayBufferRebuffer(
                    context, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
        }
    }
}
