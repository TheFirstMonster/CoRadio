/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.StorageManagerDependency
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.IntentUtils
import java.io.ByteArrayOutputStream

class FileStoreManager : StorageManagerDependency {

    private lateinit var mStorageManagerLayer: StorageManagerLayer

    init {
        DependencyRegistryCommonUi.injectStorageManagerLayer(this)
    }

    override fun configureWith(storageManagerLayer: StorageManagerLayer) {
        mStorageManagerLayer = storageManagerLayer
    }

    fun upload(activity: AppCompatActivity, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, getUploadIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun upload(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, getUploadIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun download(activity: AppCompatActivity, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, getDownloadIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun download(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, getDownloadIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun onActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        if (resultCode != Activity.RESULT_OK) {
            AppLogger.e("$TAG activity result is not OK")
            onFailure()
            return
        }
        if (intent == null) {
            AppLogger.e("$TAG activity result has no intent")
            onFailure()
            return
        }
        when (requestCode) {
            REQUEST_CODE_CREATE_FILE -> {
                val uri: Uri? = intent.data
                AppLogger.d("Save data file to:$uri")
                if (uri != null) {
                    context.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                        val data = hashMapOf(
                            KEY_FAV to mStorageManagerLayer.getAllFavoritesAsString(),
                            KEY_LOC to mStorageManagerLayer.getAllDeviceLocalsAsString()
                        )
                        outputStream.write(AppUtils.encodeBase64(AppUtils.serializeMap(data)))
                        onSuccess()
                    } ?: run {
                        AppLogger.e("Error opening output stream for uri: $uri")
                        onFailure()
                    }
                } else {
                    onFailure()
                }
            }

            REQUEST_CODE_OPEN_FILE -> {
                val uri: Uri? = intent.data
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val buffer = ByteArray(2046)
                            val byteArrayOutputStream = ByteArrayOutputStream()
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                byteArrayOutputStream.write(buffer, 0, bytesRead)
                            }
                            val fileContent = byteArrayOutputStream.toByteArray()
                            val data = AppUtils.deserializeMap(AppUtils.decodeBase64(fileContent))
                            if (data.isEmpty().not()) {
                                val fav: String? = data[KEY_FAV]
                                if (!fav.isNullOrEmpty()) {
                                    mStorageManagerLayer.mergeFavorites(fav)
                                }
                                val loc: String? = data[KEY_LOC]
                                if (!loc.isNullOrEmpty()) {
                                    mStorageManagerLayer.mergeDeviceLocals(loc)
                                }
                            }
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error reading file content", e)
                        onFailure()
                    }
                } ?: onFailure()
            }
        }
    }

    private fun getUploadIntent(): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType(FILE_TYPE)
        intent.putExtra(Intent.EXTRA_TITLE, FILE_NAME)
        return intent
    }

    private fun getDownloadIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = FILE_TYPE
        return intent
    }

    companion object {

        private const val TAG = "FSM"
        private const val KEY_FAV = "favorites"
        private const val KEY_LOC = "locals"
        private const val FILE_NAME = "coradio_data"
        private const val FILE_TYPE = "text/plain"
        private const val REQUEST_CODE_CREATE_FILE = 1234
        private const val REQUEST_CODE_OPEN_FILE = 5678
    }
}
