/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy [chernyshov.yuriy@gmail.com]
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

import android.util.Log

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 7/26/16
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * A helper class designed to assist with logging.
 */
object AnalyticsUtils {

    fun logMessage(message: String, severity: Int = Log.DEBUG) {
        when (severity) {
            Log.ERROR -> AppLogger.e(message)
            else -> AppLogger.d(message)
        }
    }

    fun logUnsupportedPlaylist(value: String) {
        AppLogger.d("Unsupported playlist: $value")
    }

    fun logUnsupportedInvalidPlaylist(value: String) {
        AppLogger.d("Unsupported invalid playlist: $value")
    }

    fun logMetadata(value: String) {
        AppLogger.d("Metadata: $value")
    }

    fun logAboutOpen() {
        AppLogger.d("About page opened")
    }

    fun logEmptyLocalConfig(msg: String) {
        AppLogger.w(msg)
    }

    fun logBitmapDecode(value: String, bytesSize: Int) {
        AppLogger.d("Bitmap decode failed - URL: $value, Size: $bytesSize bytes")
    }

    fun logGDriveFileDeleted(value: String) {
        AppLogger.d("Google Drive file deleted: $value")
    }
}
