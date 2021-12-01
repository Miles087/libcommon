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

import com.serenegiant.glutils.EffectDrawer2D;
import com.serenegiant.glutils.GLDrawer2D;
import com.serenegiant.glutils.GLManager;
import com.serenegiant.glutils.GLUtils;
import com.serenegiant.glutils.RendererTarget;
import com.serenegiant.math.Fraction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

public class EffectPipeline extends ProxyPipeline {
	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = EffectPipeline.class.getSimpleName();

	@NonNull
	private final Object mSync = new Object();
	@NonNull
	private final GLManager mManager;

	private volatile boolean mReleased;
	@Nullable
	private EffectDrawer2D mDrawer;
	@Nullable
	private RendererTarget mRendererTarget;
	/**
	 * 映像効果付与してそのまま次のIPipelineへ送るかSurfaceへ描画するか
	 */
	private boolean mEffectOnly;

	/**
	 * コンストラクタ
	 * @param manager
	 * @param manager
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	public EffectPipeline(@NonNull final GLManager manager)
			throws IllegalStateException, IllegalArgumentException {
		this(manager,  null, null);
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
	public EffectPipeline(
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
					mDrawer = new EffectDrawer2D(manager.isisGLES3(), true, mEffectListener);
					createTarget(surface, maxFps);
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
	 * 描画先のSurfaceを差し替え, 最大フレームレートの制限をしない
	 * 対応していないSurface形式の場合はIllegalArgumentExceptionを投げる
	 * @param surface nullまたはSurface/SurfaceHolder/SurfaceTexture/SurfaceView
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	public void setSurface(@Nullable final Object surface)
		throws IllegalStateException, IllegalArgumentException {

		setSurface(surface, null);
	}

	/**
	 * 描画先のSurfaceを差し替え
	 * 対応していないSurface形式の場合はIllegalArgumentExceptionを投げる
	 * @param surface nullまたはSurface/SurfaceHolder/SurfaceTexture/SurfaceView
	 * @param maxFps 最大フレームレート, nullまたはFraction#ZEROなら制限なし
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
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
				createTarget(surface, maxFps);
			}
		});
	}

	@Override
	public boolean isValid() {
		return !mReleased && mManager.isValid();
	}

	private int cnt;
	@WorkerThread
	@Override
	public void onFrameAvailable(final boolean isOES, final int texId, @NonNull final float[] texMatrix) {
		if (!mReleased) {
			synchronized (mSync) {
				if ((mRendererTarget != null)
					&& mRendererTarget.isEnabled()
					&& mRendererTarget.isValid()) {
					if (isOES != mDrawer.isOES()) {
						// 初回またはIPipelineを繋ぎ変えたあとにテクスチャが変わるかもしれない
						mDrawer.release();
						mDrawer = new EffectDrawer2D(mManager.isisGLES3(), isOES, mEffectListener);
					}
					mRendererTarget.draw(mDrawer.getDrawer(), texId, texMatrix);
					if (DEBUG && (++cnt % 100) == 0) {
						Log.v(TAG, "onFrameAvailable:" + cnt);
					}
				}
				if (mEffectOnly) {
					// FIXME 映像効果付与したテクスチャを次へ渡す
					super.onFrameAvailable(isOES, texId, texMatrix);
				} else {
					// こっちはオリジナルのテクスチャを渡す
					super.onFrameAvailable(isOES, texId, texMatrix);
				}
			}
		}
	}

//--------------------------------------------------------------------------------
	/**
	 * 映像効果をリセット
	 * @throws IllegalStateException
	 */
	public void resetEffect() throws IllegalStateException {
		if (isValid()) {
			mManager.runOnGLThread(new Runnable() {
				@Override
				public void run() {
					synchronized (mSync) {
						if (mDrawer != null) {
							mDrawer.resetEffect();
						}
					}
				}
			});
		} else {
			throw new IllegalStateException("already released!");
		}
	}

	/**
	 * 映像効果をセット
	 * @param effect
	 * @throws IllegalStateException
	 */
	public void setEffect(final int effect) throws IllegalStateException {
		if (DEBUG) Log.v(TAG, "setEffect:" + effect);
		if (isValid()) {
			mManager.runOnGLThread(new Runnable() {
				@Override
				public void run() {
					if (DEBUG) Log.v(TAG, "setEffect#run:" + effect);
					synchronized (mSync) {
						if (mDrawer != null) {
							mDrawer.setEffect(effect);
						}
					}
				}
			});
		} else {
			throw new IllegalStateException("already released!");
		}
	}

	public int getCurrentEffect() {
		if (DEBUG) Log.v(TAG, "getCurrentEffect:" + mDrawer.getCurrentEffect());
		synchronized (mSync) {
			return mDrawer != null ? mDrawer.getCurrentEffect() : 0;
		}
	}
//--------------------------------------------------------------------------------
	final EffectDrawer2D.EffectListener mEffectListener
		= new EffectDrawer2D.EffectListener() {
			@Override
			public boolean onChangeEffect(final int effect, @NonNull final GLDrawer2D drawer) {
				return EffectPipeline.this.onChangeEffect(effect, drawer);
			}
		};

	private void createTarget(@Nullable final Object surface, @Nullable final Fraction maxFps) {
		synchronized (mSync) {
			if (mRendererTarget != surface) {
				if (mRendererTarget != null) {
					mRendererTarget.release();
					mRendererTarget = null;
				}
				if (GLUtils.isSupportedSurface(surface)) {
					mRendererTarget = RendererTarget.newInstance(
						mManager.getEgl(), surface, maxFps != null ? maxFps.asFloat() : 0);
					mEffectOnly = false;
				} else {
					mEffectOnly = true;
				}
			}
		}
	}

	protected boolean onChangeEffect(final int effect, @NonNull final GLDrawer2D drawer) {
		return false;
	}
}