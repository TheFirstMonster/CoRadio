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

package app.coradio.shared.view.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.media3.common.MediaLibraryInfo
import app.coradio.shared.R
import app.coradio.shared.utils.AnalyticsUtils
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.IntentUtils
import app.coradio.shared.utils.findImageView
import app.coradio.shared.utils.findTextView

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class AboutDialog : BaseDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_about,
            requireActivity().findViewById(R.id.dialog_about_root)
        )
        setWindowDimensions(view, 0.9f, 0.9f)
        val context = requireContext()
        val exoPlayerVersion = view.findTextView(R.id.about_exo_player_ver_link_view)
        val exoPlayerVersionText = getString(R.string.about_exo_text) + " " + MediaLibraryInfo.VERSION
        exoPlayerVersion.text = exoPlayerVersionText

        val versionText = AppUtils.getApplicationVersion(context) + "." +
                AppUtils.getApplicationVersionCode(context)
        val versionCodeView = view.findTextView(R.id.dialog_about_version_view)
        versionCodeView.text = versionText

        setOnClickOnText(context, view, R.id.about_author_link_view, AUTHOR_PROFILE_URL)
        setOnClickOnText(context, view, R.id.about_project_link_view, PROJECT_HOME_URL)
        setOnClickOnText(context, view, R.id.about_exo_player_ver_link_view, EXO_PLAYER_URL)
        setOnClickOnText(context, view, R.id.about_report_issue_link_view, REPORT_ISSUE_URL)
        setOnClickOnText(context, view, R.id.about_radio_browser_link_view, RADIO_BROWSER_URL)
        setOnClickOnText(context, view, R.id.about_web_radio_link_view, WEB_RADIO_URL)
        setOnClickOnText(context, view, R.id.about_playlist_parser_name_view, PLAY_LIST_PARSER_URL)
        setOnClickOnText(context, view, R.id.about_easy_swipe_name_view, SWIPE_EFFECT_URL)

        AnalyticsUtils.logAboutOpen()

        return createAlertDialog(view)
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = AboutDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"

        /**
         * My profile's url.
         */
        private const val AUTHOR_PROFILE_URL = "https://www.linkedin.com/in/yurii-chernyshov"
        /**
         * Project's url
         */
        private const val PROJECT_HOME_URL = "https://github.com/ChernyshovYuriy/CoRadio"

        private const val EXO_PLAYER_URL = "https://github.com/google/ExoPlayer"

        private const val PLAY_LIST_PARSER_URL = "https://github.com/wseemann/JavaPlaylistParser"

        private const val SWIPE_EFFECT_URL = "https://github.com/xenione/swipe-maker"

        private const val REPORT_ISSUE_URL = "https://github.com/ChernyshovYuriy/CoRadio/issues"

        private const val RADIO_BROWSER_URL = "https://www.radio-browser.info"

        private const val WEB_RADIO_URL = "https://jcorporation.github.io/webradiodb"

        private fun setOnClickOnText(context: Context, view: View, viewId: Int, linkUrl: String) {
            view.findTextView(viewId).setOnClickListener {
                IntentUtils.startActivitySafe(context, IntentUtils.makeUrlBrowsableIntent(linkUrl))
            }
        }

        private fun setOnClickOnImage(context: Context, view: View, viewId: Int, linkUrl: String) {
            view.findImageView(viewId).setOnClickListener {
                IntentUtils.startActivitySafe(context, IntentUtils.makeUrlBrowsableIntent(linkUrl))
            }
        }
    }
}
