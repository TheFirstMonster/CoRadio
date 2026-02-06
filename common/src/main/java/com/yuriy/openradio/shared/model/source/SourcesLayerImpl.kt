/*
 * Copyright 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model.source

import android.content.Context
import app.coradio.shared.model.storage.SourceStorage
import app.coradio.shared.utils.AppLogger
import java.lang.ref.WeakReference

class SourcesLayerImpl(context: Context) : SourcesLayer {

    private val mSources = Source.values().toSet()
    private val mPersistence = SourceStorage(WeakReference(context))

    override fun getAllSources(): Set<Source> {
        return mSources
    }

    override fun getActiveSource(): Source {
        val idx = mPersistence.getIntValue(KEY_IDX, 0)
        val source = mSources.elementAt(idx)
        AppLogger.d("Get active source $source")
        return source
    }

    override fun setActiveSource(source: Source) {
        AppLogger.d("Set active source $source")
        mPersistence.putIntValue(KEY_IDX, source.srcId)
    }

    companion object {

        private const val KEY_IDX = "ActiveSrcIdx"
    }
}
