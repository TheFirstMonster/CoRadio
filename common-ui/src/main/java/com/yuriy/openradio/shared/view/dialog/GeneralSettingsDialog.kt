/*
 * Copyright 2017 - 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import app.coradio.shared.R
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.LoggingLayerDependency
import app.coradio.shared.dependencies.MediaPresenterDependency
import app.coradio.shared.model.logging.LoggingLayer
import app.coradio.shared.model.storage.AppPreferencesManager
import app.coradio.shared.permission.PermissionChecker
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.service.CoRadioStore
import app.coradio.shared.service.location.LocationService
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.SafeToast
import app.coradio.shared.utils.findButton
import app.coradio.shared.utils.findCheckBox
import app.coradio.shared.utils.findEditText
import app.coradio.shared.utils.findSeekBar
import app.coradio.shared.utils.findSpinner
import app.coradio.shared.utils.findTextView
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible
import app.coradio.shared.view.list.CountriesArrayAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class GeneralSettingsDialog : BaseDialogFragment(), MediaPresenterDependency, LoggingLayerDependency {

    private lateinit var mUserAgentEditView: EditText
    private lateinit var mMediaPresenter: MediaPresenter
    private lateinit var mLoggingLayer: LoggingLayer

    override fun configureWith(mediaPresenter: MediaPresenter) {
        mMediaPresenter = mediaPresenter
    }

    override fun configureWith(loggingLayer: LoggingLayer) {
        mLoggingLayer = loggingLayer
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        DependencyRegistryCommonUi.injectLoggingLayer(this)
        DependencyRegistryCommonUi.inject(this)
        val activity = requireActivity()
        val context = requireContext()
        val view = inflater.inflate(
            R.layout.dialog_general_settings,
            activity.findViewById(R.id.dialog_general_settings_root)
        )
        setWindowDimensions(view, 0.9f, 0.9f)
        val titleText = activity.getString(R.string.app_settings_title)
        val title = view.findTextView(R.id.dialog_settings_title_view)
        title.text = titleText
        val lastKnownRadioStationEnabled = AppPreferencesManager.lastKnownRadioStationEnabled(context)
        val lastKnownRadioStationEnableCheckView = view.findCheckBox(
            R.id.settings_dialog_enable_last_known_radio_station_check_view
        )
        lastKnownRadioStationEnableCheckView.isChecked = lastKnownRadioStationEnabled
        lastKnownRadioStationEnableCheckView.setOnClickListener { view1: View ->
            val checked = (view1 as CheckBox).isChecked
            AppPreferencesManager.lastKnownRadioStationEnabled(context, checked)
        }
        mUserAgentEditView = view.findEditText(R.id.user_agent_input_view)
        mUserAgentEditView.setText(AppUtils.getUserAgent(context))
        val userAgentCheckView = view.findCheckBox(R.id.user_agent_check_view)
        userAgentCheckView.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            AppPreferencesManager.isCustomUserAgent(context, isChecked)
            mUserAgentEditView.isEnabled = isChecked
        }
        val isCustomUserAgent = AppPreferencesManager.isCustomUserAgent(context)
        userAgentCheckView.isChecked = isCustomUserAgent
        mUserAgentEditView.isEnabled = isCustomUserAgent
        val masterVolumeSeekBar = view.findSeekBar(R.id.master_vol_seek_bar)
        masterVolumeSeekBar.progress =
            AppPreferencesManager.getMasterVolume(context, CoRadioService.MASTER_VOLUME_DEFAULT)
        masterVolumeSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // Not used.
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    // Not used.
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val bundle = CoRadioStore.makeMasterVolumeChangedBundle(seekBar.progress)
                        mMediaPresenter.getServiceCommander()
                            .sendCommand(CoRadioService.CMD_MASTER_VOLUME_CHANGED, bundle)
                    }
                }
            }
        )
        val btAutoRestart = view.findCheckBox(R.id.bt_auto_restart_check_view)
        btAutoRestart.isChecked = AppPreferencesManager.isBtAutoPlay(context)
        btAutoRestart.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (AppUtils.hasVersionS()) {
                if (!PermissionChecker.isBluetoothConnectGranted(context)) {
                    PermissionChecker.requestBluetoothPermission(activity, view)
                } else {
                    AppPreferencesManager.setBtAutoPlay(context, isChecked)
                }
            } else {
                AppPreferencesManager.setBtAutoPlay(context, isChecked)
            }
        }
        val clearCache = view.findButton(R.id.clear_cache_btn)
        clearCache.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                mMediaPresenter.getServiceCommander().sendCommand(CoRadioService.CMD_CLEAR_CACHE)
            }
        }
        val array = LocationService.getCountriesWithLocation(context)
        val countryCode = mMediaPresenter.getCountryCode()
        var idx = 0
        for ((i, item) in array.withIndex()) {
            if (item.code == countryCode) {
                idx = i
                break
            }
        }

        val adapter = CountriesArrayAdapter(context, array)
        val spinner = view.findSpinner(R.id.default_country_spinner)
        spinner.adapter = adapter
        spinner.setSelection(idx)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val code = array[position].code
                mMediaPresenter.onLocationChanged(code)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Not used.
            }
        }

        val sendLogsProgress = view.findViewById<ProgressBar>(R.id.send_logs_progress_view)
        val sendLogs = view.findButton(R.id.send_logs_btn)
        sendLogs.setOnClickListener {
            sendLogsProgress.visible()
            mLoggingLayer.collectAdbLogs(
                {
                    mLoggingLayer.sendLogsViaEmail(
                        it,
                        {
                            activity.runOnUiThread {
                                sendLogsProgress.gone()
                                SafeToast.showAnyThread(context, context.getString(R.string.success))
                            }
                        },
                        {
                            activity.runOnUiThread { sendLogsProgress.gone() }
                            SafeToast.showAnyThread(context, context.getString(R.string.failure))
                        }
                    )
                },
                {
                    activity.runOnUiThread { sendLogsProgress.gone() }
                    SafeToast.showAnyThread(context, context.getString(R.string.failure))
                }
            )
        }

        return createAlertDialog(view)
    }

    override fun onPause() {
        super.onPause()
        saveCustomUserAgent()
    }

    private fun saveCustomUserAgent() {
        if (this::mUserAgentEditView.isInitialized.not()) {
            return
        }
        val context = activity ?: return
        val userAgent = mUserAgentEditView.text.toString().trim { it <= ' ' }
        if (userAgent.isEmpty()) {
            SafeToast.showAnyThread(context, getString(R.string.user_agent_empty_warning))
            return
        }
        AppPreferencesManager.setCustomUserAgent(context, userAgent)
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = GeneralSettingsDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
    }
}
