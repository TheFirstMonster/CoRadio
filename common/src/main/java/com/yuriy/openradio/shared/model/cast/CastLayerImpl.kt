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
package app.coradio.shared.model.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import app.coradio.shared.utils.AppLogger
import java.util.concurrent.Executors

class CastLayerImpl(private val mContext: Context) : CastLayer {

    private val mExecutor = Executors.newSingleThreadExecutor()
    private var mCastContext: CastContext? = null

    init {
        AppLogger.i("Cast context impl")
    }

    override fun getCastContext(): CastContext? {
        AppLogger.d("Get Cast context $mCastContext")
        if (mCastContext != null) {
            return mCastContext
        }
        // Initialize the Cast context.
        // This is required so that the media route button can be created in the AppBar.
        val task = CastContext.getSharedInstance(mContext, mExecutor)
        task.addOnCompleteListener {
            if (it.isSuccessful) {
                AppLogger.i("Get Cast context successfully")
                mCastContext = it.result
            } else {
                val exception = it.exception
                AppLogger.e("Get Cast context exception", exception)
            }
        }
        task.addOnFailureListener {
            AppLogger.e("Get Cast context failed", it)
        }
        AppLogger.d("Get Cast context requested")
        return null
    }
}
