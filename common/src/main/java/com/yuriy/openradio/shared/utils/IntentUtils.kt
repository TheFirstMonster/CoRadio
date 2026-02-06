/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
object IntentUtils {

    /**
     * Make intent to navigate to provided url.
     *
     * @param url Url to navigate to.
     * @return [Intent].
     */
    fun makeUrlBrowsableIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    /**
     * Dump content of [Intent]'s [Bundle] into [String].
     *
     * @param intent [Intent] to process.
     * @return [String] representation of [Bundle].
     */
    fun intentBundleToString(intent: Intent?): String {
        return if (intent == null) {
            "Intent[null]"
        } else bundleToString(intent.extras)
    }

    /**
     * Dump content of [Bundle] into [String].
     *
     * @param bundle [Bundle] to process.
     * @return [String] representation of [Bundle].
     */
    fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) {
            return "Bundle[null]"
        }
        val size = try {
            bundle.size()
        } catch (e: Exception) {
            // Address:
            // BadParcelableException: ClassNotFoundException when unmarshalling:
            // com.google.android.apps.docs.common.drivecore.data.CelloEntrySpec
            AppLogger.e("Can not process bundles", e)
        }
        if (size == 0) {
            return "Bundle[]"
        }
        val builder = StringBuilder("Bundle[")
        try {
            for (key in bundle.keySet()) {
                builder.append(key).append(":").append(if (bundle[key] != null) bundle[key] else "NULL")
                builder.append("|")
            }
            builder.delete(builder.length - 1, builder.length)
        } catch (e: Exception) {
            AppLogger.e("Intent's bundle to string", e)
        }
        builder.append("]")
        return builder.toString()
    }

    fun startActivitySafe(context: Context?, intent: Intent) {
        if (context == null) {
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            AppLogger.e("Can not start activity", e)
        }
    }

    fun startActivityForResultSafe(fragment: Fragment?, intent: Intent, requestCode: Int): Boolean {
        if (fragment == null) {
            return false
        }
        try {
            fragment.startActivityForResult(intent, requestCode)
        } catch (e: Throwable) {
            AppLogger.e("Can not start activity for result", e)
            return false
        }
        return true
    }

    fun startActivityForResultSafe(activity: AppCompatActivity?, intent: Intent, requestCode: Int): Boolean {
        if (activity == null) {
            return false
        }
        try {
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Throwable) {
            AppLogger.e("Can not start activity for result", e)
            return false
        }
        return true
    }

    fun registerForActivityResultIntrl(
        caller: ActivityResultCaller,
        callback: (data: Intent?) -> Unit
    ): ActivityResultLauncher<Intent> {
        return caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            callback(result.data)
        }
    }

    @Suppress("DEPRECATION")
    inline fun <reified T> getParcelableExtra(key: String, intent: Intent): T? {
        if (intent.hasExtra(key).not()) {
            return null
        }
        return if (AppUtils.hasVersionTiramisu()) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }
}
