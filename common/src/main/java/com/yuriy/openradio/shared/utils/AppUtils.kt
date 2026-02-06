/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Base64
import android.util.DisplayMetrics
import android.webkit.MimeTypeMap
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import app.coradio.R
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.storage.AppPreferencesManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import java.util.TreeSet

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 11/29/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [AppUtils] is a helper class which holds various help-methods
 */
object AppUtils {

    /**
     * Time out for the stream to decide whether there is response or not, ms.
     */
    const val TIME_OUT = 2000
    const val UTF8 = "UTF-8"
    const val EMPTY_STRING = ""

    const val USE_CUR_SEARCH_QUERY = "<<<:::>>>"
    private const val KEY_SEARCH_QUERY = "KEY_SEARCH_QUERY"

    /**
     * Tag string to use in logging message.
     */
    private val CLASS_NAME = AppUtils::class.java.simpleName

    /**
     * Whether or not device supports Location feature.
     *
     * @param context Context of the callee.
     * @return
     */
    fun hasLocation(context: Context): Boolean {
        val packageManager = context.packageManager ?: return false
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
    }

    /**
     * Get application's version name.
     *
     * @param context Application context.
     * @return Application Version name.
     */
    fun getApplicationVersion(context: Context): String {
        val packageInfo = getPackageInfo(context)
        return if (packageInfo != null) {
            packageInfo.versionName
        } else {
            AppLogger.w("$CLASS_NAME Can't get application version")
            "?"
        }
    }

    /**
     * @return PackageInfo for the current application or null if the PackageManager could not be
     * contacted.
     */
    private fun getPackageInfo(context: Context): PackageInfo? {
        val packageManager = context.packageManager
        if (packageManager == null) {
            AppLogger.w("$CLASS_NAME Package manager is NULL")
            return null
        }
        val packageName: String
        return try {
            packageName = context.packageName
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            AppLogger.e("Get pck info", e)
            null
        } catch (e: RuntimeException) {
            // To catch RuntimeException("Package manager has died") that can occur on some
            // version of Android,
            // when the remote PackageManager is unavailable. I suspect this sometimes occurs
            // when the App is being reinstalled.
            AppLogger.e("Get pck info", e)
            null
        } catch (e: Throwable) {
            AppLogger.e("$CLASS_NAME Package manager", e)
            null
        }
    }

    /**
     * This is a helper method with allows to prevent get a list of the predefined categories,
     * in order to do not show an empty category.
     *
     * @return Collection of the categories.
     */
    fun predefinedCategories(): Set<String> {
        val predefinedCategories = TreeSet<String>()
        predefinedCategories.add("Classical")
        predefinedCategories.add("Country")
        predefinedCategories.add("Decades")
        predefinedCategories.add("Electronic")
        predefinedCategories.add("Folk")
        predefinedCategories.add("International")
        predefinedCategories.add("Jazz")
        predefinedCategories.add("Misc")
        predefinedCategories.add("News")
        predefinedCategories.add("Pop")
        predefinedCategories.add("R&B/Urban")
        predefinedCategories.add("Rap")
        predefinedCategories.add("Reggae")
        predefinedCategories.add("Rock")
        predefinedCategories.add("Talk & Speech")
        predefinedCategories.add("University Radio")
        return predefinedCategories
    }

    /**
     * @return Persistent value of the User Agent.
     *
     * //TODO: Find a better way to handle this. This value is not changing often, need to cache it.
     */
    @UnstableApi
    fun getUserAgent(context: Context): String {
        val defaultValue = Util.getUserAgent(context, context.getString(R.string.app_name_user_agent))
        return if (AppPreferencesManager.isCustomUserAgent(context)) AppPreferencesManager.getCustomUserAgent(
            context,
            defaultValue
        ) else defaultValue
    }

    /**
     * @param context
     * @return
     */
    fun getShortestScreenSize(context: FragmentActivity): Int {
        val displayMetrics = DisplayMetrics()
        context.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        return height.coerceAtMost(width)
    }

    fun getApplicationVersionCode(context: Context): Long {
        val packageInfo = getPackageInfo(context)
        return if (packageInfo != null) {
            PackageInfoCompat.getLongVersionCode(packageInfo)
        } else {
            AppLogger.w("Can't get application code")
            0L
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.KITKAT)
    fun hasVersionKitKat(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
    fun hasVersionLollipop(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    fun hasVersionM(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    fun hasVersionP(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun hasVersionS(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun hasVersionTiramisu(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun makeSearchQueryBundle(queryString: String): Bundle {
        val bundle = Bundle()
        bundle.putString(KEY_SEARCH_QUERY, queryString)
        return bundle
    }

    fun getSearchQueryFromBundle(queryBundle: Bundle): String {
        return queryBundle.getString(KEY_SEARCH_QUERY, USE_CUR_SEARCH_QUERY)
    }

    fun getDensityDpi(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }

    /**
     * Return whether the given application package name is on the device's power allowlist.
     * Apps can be placed on the allowlist through the settings UI invoked by
     * android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS.
     * Being on the power allowlist means that the system will not apply most power saving features to the app.
     * Guardrails for extreme cases may still be applied.
     */
    @SuppressLint("NewApi")
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (hasVersionM().not()) {
            return true
        }
//        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//            .setData(Uri.parse("package:$packageName"))
//        getContext().startActivity(settingsIntent)
        val pwrm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val name = context.applicationContext.packageName
        return pwrm.isIgnoringBatteryOptimizations(name)
    }

    fun isSameCatalogue(newMediaId: String, prevMediaId: String): Boolean {
        if (newMediaId == MediaId.MEDIA_ID_FAVORITES_LIST) {
            return false
        }
        if (newMediaId == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST) {
            return false
        }
        return newMediaId != MediaId.MEDIA_ID_ROOT && newMediaId == prevMediaId
    }

    /**
     * Utility method to extract stream mime type from the stream extension (if exists).
     */
    fun getMimeTypeFromUri(uri: Uri): String {
        val mime: String =
            when (MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase(Locale.getDefault())) {
                "aacp", "aac" -> MimeTypes.AUDIO_AAC
                "ac3" -> MimeTypes.AUDIO_AC3
                "ac4" -> MimeTypes.AUDIO_AC4
                "flac" -> MimeTypes.AUDIO_FLAC
                "mp3" -> MimeTypes.AUDIO_MPEG
                "ogg", "oga" -> MimeTypes.AUDIO_OGG
                "opus" -> MimeTypes.AUDIO_OPUS
                "wav" -> MimeTypes.AUDIO_WAV
                "weba" -> MimeTypes.AUDIO_WEBM
                "m4a" -> "audio/m4a"
                "m3u", "m3u8" -> MimeTypes.APPLICATION_M3U8
                "ts" -> MimeTypes.VIDEO_MP2T
                else -> MimeTypes.AUDIO_UNKNOWN
            }
        return mime
    }

    fun serializeMap(map: Map<String, String>): ByteArray {
        return try {
            ByteArrayOutputStream().use {
                ObjectOutputStream(it).use { objectOutputStream ->
                    objectOutputStream.writeObject(map)
                }
                it.toByteArray()
            }
        } catch (e: Exception) {
            AppLogger.e("Error serializing map", e)
            byteArrayOf()
        }
    }

    fun deserializeMap(data: ByteArray?): Map<String, String> {
        if (data == null) {
            return emptyMap()
        }
        return try {
            ByteArrayInputStream(data).use {
                ObjectInputStream(it).use { objectInputStream ->
                    @Suppress("UNCHECKED_CAST")
                    objectInputStream.readObject() as Map<String, String>
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Error deserializing map", e)
            emptyMap()
        }
    }

    fun encodeBase64(byteData: ByteArray): ByteArray {
        return Base64.encode(byteData, Base64.DEFAULT)
    }

    fun decodeBase64(byteData: ByteArray): ByteArray {
        return Base64.decode(byteData, Base64.DEFAULT)
    }
}
