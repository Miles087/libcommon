package com.serenegiant.glpipeline;
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

import android.util.Log;

import com.serenegiant.glutils.GLDrawer2D;
import com.serenegiant.glutils.GLManager;
import com.serenegiant.glutils.GLUtils;
import com.serenegiant.glutils.RendererTarget;
import com.serenegiant.math.Fraction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * テクスチャが更新されたときにSurfaceへ転送するIPipeline実装
 */
public class SurfacePipeline extends ProxyPipeline implements ISurfacePipeline {
	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = SurfacePipeline.class.getSimpleName();

	@NonNull
	private final Object mSync = new Object();
	@NonNull
	private final GLManager mManager;

	private volatile boolean mReleased;
	@Nullable
	private GLDrawer2D mDrawer;
	@Nullable
	private RendererTarget mRendererTarget;

	/**
	 * コンストラクタ
	 * @param manager
	 * @param manager
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	public SurfacePipeline(@NonNull final GLManager manager)
			throws IllegalStateException, IllegalArgumentException {
		this(manager, null, null);
	}

	/**
	 * コンストラクタ
	 * @param manager
	 * 対応していないSurface形式の場合はIllegalArgumentExceptionを投げる
	 * @param surface nullまたはSurface/SurfaceHolder/SurfaceTexture/SurfaceView
	 * @param maxFps 最大フレームレート, nullまたはFraction#ZEROなら制限なし
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	public SurfacePipeline(
		@NonNull final GLManager manager,
		@Nullable final Object surface, @Nullable final Fraction maxFps)
			throws IllegalStateException, IllegalArgumentException {

		super();
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		if ((surface != null) && !GLUtils.isSupportedSurface(surface)) {
			throw new IllegalArgumentException("Unsupported surface type!," + surface);
		}
		mManager = manager;
		manager.runOnGLThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mSync) {
					mDrawer = GLDrawer2D.create(manager.isGLES3(), true);
					if (surface != null) {
						mRendererTarget = RendererTarget.newInstance(
							manager.getEgl(), surface, maxFps != null ? maxFps.asFloat() : 0);
					}
				}
			}
		});
	}

	@Override
	public void release() {
		if (DEBUG) Log.v(TAG, "release:");
		if (!mReleased) {
			mReleased = true;
			mManager.runOnGLThread(new Runnable() {
				@Override
				public void run() {
					synchronized (mSync) {
						if (mDrawer != null) {
							mDrawer.release();
							mDrawer = null;
						}
						if (mRendererTarget != null) {
							mRendererTarget.release();
							mRendererTarget = null;
						}
					}
				}
			});
		}
		super.release();
	}

	/**
	 * ISurfacePipelineの実装
	 * 描画先のSurfaceを差し替え, 最大フレームレートの制限をしない
	 * 対応していないSurface形式の場合はIllegalArgumentExceptionを投げる
	 * @param surface nullまたはSurface/SurfaceHolder/SurfaceTexture/SurfaceView
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	@Override
	public void setSurface(@Nullable final Object surface)
		throws IllegalStateException, IllegalArgumentException {

		setSurface(surface, null);
	}

	/**
	 * ISurfacePipelineの実装
	 * 描画先のSurfaceを差し替え
	 * 対応していないSurface形式の場合はIllegalArgumentExceptionを投げる
	 * @param surface nullまたはSurface/SurfaceHolder/SurfaceTexture/SurfaceView
	 * @param maxFps 最大フレームレート, nullまたはFraction#ZEROなら制限なし
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	@Override
	public void setSurface(
		@Nullable final Object surface,
		@Nullable final Fraction maxFps) throws IllegalStateException, IllegalArgumentException {

		if (DEBUG) Log.v(TAG, "setSurface:" + surface);
		if (!isValid()) {
			throw new IllegalStateException("already released?");
		}
		if ((surface != null) && !GLUtils.isSupportedSurface(surface)) {
			throw new IllegalArgumentException("Unsupported surface type!," + surface);
		}
		mManager.runOnGLThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mSync) {
					if ((mRendererTarget != null) && (mRendererTarget.getSurface() != surface)) {
						// すでにRendererTargetが生成されていて描画先surfaceが変更された時
						mRendererTarget.release();
						mRendererTarget = null;
					}
					if ((mRendererTarget == null)
						&& GLUtils.isSupportedSurface(surface)) {
						mRendererTarget = RendererTarget.newInstance(
							mManager.getEgl(), surface, maxFps != null ? maxFps.asFloat() : 0);
					}
				}
			}
		});
	}

	@Override
	public boolean isValid() {
		// super#isValidはProxyPipelineなので常にtrueを返す
		return !mReleased && mManager.isValid();
	}

	private int cnt;
	@WorkerThread
	@Override
	public void onFrameAvailable(final boolean isOES, final int texId, @NonNull final float[] texMatrix) {
		super.onFrameAvailable(isOES, texId, texMatrix);
		if (!mReleased) {
			synchronized (mSync) {
				if ((mRendererTarget != null)
					&& mRendererTarget.isEnabled()
					&& mRendererTarget.isValid()) {
					if (isOES != mDrawer.isOES()) {
						// 初回またはIPipelineを繋ぎ変えたあとにテクスチャが変わるかもしれない
						mDrawer.release();
						mDrawer = GLDrawer2D.create(mManager.isGLES3(), isOES);
					}
					mRendererTarget.draw(mDrawer, texId, texMatrix);
					if (DEBUG && (++cnt % 100) == 0) {
						Log.v(TAG, "onFrameAvailable:" + cnt);
					}
				}
			}
		}
	}
}
