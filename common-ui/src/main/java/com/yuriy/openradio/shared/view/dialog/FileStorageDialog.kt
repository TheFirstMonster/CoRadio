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

package app.coradio.shared.view.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import app.coradio.shared.R
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.FileStoreManagerDependency
import app.coradio.shared.model.storage.FileStoreManager
import app.coradio.shared.utils.SafeToast
import app.coradio.shared.utils.findImageButton
import app.coradio.shared.utils.findProgressBar
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible

/**
 *
 */
class FileStorageDialog : BaseDialogFragment(), FileStoreManagerDependency {

    private lateinit var mFileStoreManager: FileStoreManager
    private lateinit var mProgress: ProgressBar

    enum class Command {
        UPLOAD, DOWNLOAD
    }

    override fun configureWith(fileStoreManager: FileStoreManager) {
        mFileStoreManager = fileStoreManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DependencyRegistryCommonUi.injectFileStoreManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AccountDialog.dismiss(parentFragmentManager)
        hideProgress()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_file_storage,
            requireActivity().findViewById(R.id.file_root)
        )
        setWindowDimensions(view, 0.9f, 0.5f)
        val download = view.findImageButton(R.id.file_storage_download_btn)
        download.setOnClickListener {
            handleCommand(Command.DOWNLOAD)
        }
        val upload = view.findImageButton(R.id.file_storage_upload_btn)
        upload.setOnClickListener {
            handleCommand(Command.UPLOAD)
        }
        mProgress = view.findProgressBar(R.id.cloud_storage_progress)
        hideProgress()
        return createAlertDialog(view)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mFileStoreManager.onActivityResult(
            requireContext().applicationContext,
            requestCode,
            resultCode,
            data,
            {
                hideProgress()
                SafeToast.showAnyThread(
                    context, getString(R.string.success)
                )
            },
            {
                hideProgress()
                SafeToast.showAnyThread(
                    context, getString(R.string.failure)
                )
            }
        )
    }

    private fun handleCommand(command: Command) {
        showProgress()
        when (command) {
            Command.UPLOAD -> {
                mFileStoreManager.upload(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        context, getString(R.string.failure)
                    )
                }
            }

            Command.DOWNLOAD -> {
                mFileStoreManager.download(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        context, getString(R.string.failure)
                    )
                }
            }
        }
    }

    private fun showProgress() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mProgress.visible()
        }
    }

    private fun hideProgress() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mProgress.gone()
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = FileStorageDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
    }
}
