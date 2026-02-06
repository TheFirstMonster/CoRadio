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
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.coradio.shared.R
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils

/**
 *
 */
class BatteryOptimizationDialog : BaseDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_about,
            requireActivity().findViewById(R.id.dialog_about_root)
        )
        setWindowDimensions(view, 0.9f, 0.9f)

        return createAlertDialog(view)
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = BatteryOptimizationDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"

        fun handle(context: Context, fragmentManager: FragmentManager) {
            val ignoringBatteryOptimizations = AppUtils.isIgnoringBatteryOptimizations(context)
            AppLogger.d("Ignoring battery optimizations:$ignoringBatteryOptimizations")
        }
    }
}
