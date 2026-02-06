/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import android.os.Bundle
import android.widget.EditText
import android.widget.ProgressBar
import androidx.fragment.app.FragmentManager
import app.coradio.shared.R
import app.coradio.shared.dependencies.CloudStoreManagerDependency
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.model.storage.CloudStoreManager
import app.coradio.shared.utils.SafeToast
import app.coradio.shared.utils.findButton
import app.coradio.shared.utils.findEditText
import app.coradio.shared.utils.findProgressBar
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible
import java.io.Serializable

/**
 * Created by Yurii Chernyshov
 * At Android Studio
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class AccountDialog : BaseDialogFragment(), CloudStoreManagerDependency {

    interface DialogDismissedListener : Serializable {
        fun onDialogDismissed()
    }

    private lateinit var mProgress: ProgressBar
    private lateinit var mCloudStoreManager: CloudStoreManager
    private lateinit var mEmail: EditText
    private lateinit var mPwd: EditText

    override fun configureWith(cloudStoreManager: CloudStoreManager) {
        mCloudStoreManager = cloudStoreManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DependencyRegistryCommonUi.injectCloudStoreManager(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_account,
            requireActivity().findViewById(R.id.account_root)
        )
        setWindowDimensions(view, 0.9f, 0.5f)
        mProgress = view.findProgressBar(R.id.account_progress_bar)
        mEmail = view.findEditText(R.id.account_email_input)
        mPwd = view.findEditText(R.id.account_pwd_input)
        val signInBtn = view.findButton(R.id.account_sign_in)
        signInBtn.setOnClickListener { signIn() }
        val resetPwdBtn = view.findButton(R.id.account_reset_pwd_btn)
        resetPwdBtn.setOnClickListener { resetPwd() }
        hideProgress()
        return createAlertDialog(view)
    }

    private fun signIn() {
        showProgress()
        mCloudStoreManager.signIn(
            requireActivity(),
            mEmail.text.toString(),
            mPwd.text.toString(),
            {
                handleAccountSuccess()
            },
            {
                hideProgress()
                SafeToast.showAnyThread(
                    context, getString(R.string.failure)
                )
            }
        )
    }

    private fun resetPwd() {
        mCloudStoreManager.sendPasswordReset(
            mEmail.text.toString(),
            {
                SafeToast.showAnyThread(
                    context, getString(R.string.success)
                )
            },
            {
                SafeToast.showAnyThread(
                    context, getString(R.string.failure)
                )
            }
        )
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

    private fun handleAccountSuccess() {
        hideProgress()
        dismiss()
        val bundle = arguments
        if (bundle != null) {
            val listener = bundle.getSerializable(KEY_LISTENER) as DialogDismissedListener?
            listener?.onDialogDismissed()
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = AccountDialog::class.java.simpleName

        private val KEY_LISTENER = "$CLASS_NAME-listener"

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"

        fun show(parentFragmentManager: FragmentManager, listener: DialogDismissedListener) {
            if (isDialogShown(parentFragmentManager)) {
                return
            }
            val bundle = Bundle()
            bundle.putSerializable(KEY_LISTENER, listener)
            val dialog = newInstance(AccountDialog::class.java.name, bundle)
            dialog.show(parentFragmentManager, DIALOG_TAG)
        }

        fun dismiss(fragmentManager: FragmentManager) {
            (fragmentManager.findFragmentByTag(DIALOG_TAG) as AccountDialog?)?.dismiss()
        }

        private fun isDialogShown(fragmentManager: FragmentManager): Boolean {
            val fragment = fragmentManager.findFragmentByTag(DIALOG_TAG)
            return fragment != null && fragment is AccountDialog
        }
    }
}
