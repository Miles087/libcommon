package com.serenegiant.widget
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2021 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/
import android.content.Context
import android.util.AttributeSet
import com.serenegiant.glutils.IRendererHolder
import com.serenegiant.glutils.IRendererHolder.RenderHolderCallback
import com.serenegiant.glutils.MixRendererHolder
import com.serenegiant.graphics.BitmapHelper
import com.serenegiant.libcommon.BuildConfig

/**
 * Sub class of GLSurfaceView to display camera preview and write video frame to capturing surface
 * FIXME 本来はカメラ映像に映像2の入力が丸く合成されて表示されるが、今は映像2入力が無いので黒い丸が合成表示される
 */
class MixCameraGLSurfaceView @JvmOverloads constructor(
	context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0)
		: AbstractCameraGLSurfaceView(context, attrs, defStyle) {

	@Synchronized
	override fun onResume() {
		super.onResume()
		val rendererHolder = rendererHolder
		if (rendererHolder is MixRendererHolder) { // とりあえずカメラ映像中央部に円形に映像2を合成する
			rendererHolder.setMask(
				BitmapHelper.genMaskImage(0,
					CameraDelegator.DEFAULT_PREVIEW_WIDTH, CameraDelegator.DEFAULT_PREVIEW_HEIGHT,
					60, 0, 100))
		}
	}

	override fun createRendererHolder(
		width: Int, height: Int,
		callback: RenderHolderCallback?): IRendererHolder {

		return MixRendererHolder(width, height, glVersion, null, 0, BuildConfig.ENABLE_VSYNC, callback)
	}

	companion object {
		private const val DEBUG = false // TODO set false on release
		private val TAG = MixCameraGLSurfaceView::class.java.simpleName
	}
}