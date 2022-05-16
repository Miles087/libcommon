package com.serenegiant.glutils;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2022 saki t_saki@serenegiant.com
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

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import static com.serenegiant.glutils.IRendererCommon.*;

public class GLUtils {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = GLUtils.class.getSimpleName();

	private GLUtils() {
		// インスタンス化を防ぐためにデフォルトコンストラクタをprivateに
	}

	private static int sSupportedGLVersion = -1;

	/**
	 * 対応しているSurfaceかどうかを確認
	 * Surface/SurfaceHolder/SurfaceTexture/SurfaceViewならtrue
	 * @param surface
	 * @return
	 */
	public static boolean isSupportedSurface(@Nullable final Object surface) {
		return ((surface instanceof Surface)
			|| (surface instanceof SurfaceHolder)
			|| (surface instanceof SurfaceTexture)
			|| (surface instanceof SurfaceView));
	}

	/**
	 * 対応しているGL|ESのバージョンを取得
	 * XXX GLES30はAPI>=18以降なんだけどAPI=18でもGLコンテキスト生成に失敗する端末があるのでAP1>=21に変更
	 * API>=21でGL_OES_EGL_image_external_essl3に対応していれば3, そうでなければ2を返す
	 * @return
	 */
	public static synchronized int getSupportedGLVersion() {
		if (sSupportedGLVersion < 1) {
			// 一度も実行されていない時
			final AtomicInteger result = new AtomicInteger(1);
			final Semaphore sync = new Semaphore(0);
			final GLContext context = new GLContext(3, null, 0);
			// ダミースレッド上でEGL/GLコンテキストを生成してエクステンション文字列をチェックする
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						context.initialize();
						String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS); // API >= 8
						if (DEBUG) Log.i(TAG, "getSupportedGLVersion:" + extensions);
						if ((extensions == null) || !extensions.contains("GL_OES_EGL_image_external")) {
							// GL_OES_EGL_image_externalが存在していない
							result.set(1);
						} else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && context.isGLES3()) {
							// API>=21でGLContextがGLES3で初期化できた時はGL_OES_EGL_image_external_essl3をチェックする
							extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS); 	// API >= 18
							result.set((extensions != null) && extensions.contains("GL_OES_EGL_image_external_essl3")
								? 3 : 2);
						} else {
							result.set(2);
						}
					} catch (final Exception e) {
						Log.w(TAG, e);
					} finally {
						context.release();
						sync.release();
					}
				}
			}).start();
			try {
				if (sync.tryAcquire(500, TimeUnit.MILLISECONDS)) {
					sSupportedGLVersion = result.get();
				}
			} catch (final InterruptedException e) {
				// ignore
			}
		}
		if (DEBUG) Log.i(TAG, "getSupportedGLVersion:" + sSupportedGLVersion);
		return sSupportedGLVersion;
	}

	/**
	 * モデルビュー変換行列に左右・上下反転をセット
	 * @param mvp
	 * @param mirror
	 */
	public static void setMirror(@NonNull @Size(min=16) final float[] mvp, @MirrorMode final int mirror) {
		switch (mirror) {
		case MIRROR_NORMAL:
			mvp[0] = Math.abs(mvp[0]);
			mvp[5] = Math.abs(mvp[5]);
			break;
		case MIRROR_HORIZONTAL:
			mvp[0] = -Math.abs(mvp[0]);	// flip left-right
			mvp[5] = Math.abs(mvp[5]);
			break;
		case MIRROR_VERTICAL:
			mvp[0] = Math.abs(mvp[0]);
			mvp[5] = -Math.abs(mvp[5]);	// flip up-side down
			break;
		case MIRROR_BOTH:
			mvp[0] = -Math.abs(mvp[0]);	// flip left-right
			mvp[5] = -Math.abs(mvp[5]);	// flip up-side down
			break;
		}
	}

	/**
	 * 現在のモデルビュー変換行列をxy平面で指定した角度回転させる
	 * @param mvp
	 * @param degrees
	 */
	public static void rotate(@NonNull @Size(min=16) final float[] mvp, final int degrees) {
		if ((degrees % 180) != 0) {
			Matrix.rotateM(mvp, 0, degrees, 0.0f, 0.0f, 1.0f);
		}
	}

	/**
	 * モデルビュー変換行列にxy平面で指定した角度回転させた回転行列をセットする
	 * @param mvp
	 * @param degrees
	 */
	public static void setRotation(@NonNull @Size(min=16) final float[] mvp, final int degrees) {
		Matrix.setIdentityM(mvp, 0);
		if ((degrees % 180) != 0) {
			Matrix.rotateM(mvp, 0, degrees, 0.0f, 0.0f, 1.0f);
		}
	}

	public static int gLTextureUnit2Index(final int glTextureUnit) {
		return (glTextureUnit >= GLES20.GL_TEXTURE0) && (glTextureUnit <= GLES20.GL_TEXTURE31)
			? glTextureUnit - GLES20.GL_TEXTURE0 : 0;
	}

	/**
	 * GLES20.glReadPixelsのヘルパーメソッド
	 * RGBA8888として読み取る(=1ピクセル4バイト)
	 * orderをLITTLE_ENDIANにセットするのでBitmap#copyPixelsFromBufferへ直接引き渡すことができる
	 * @param buffer nullまたはサイズが小さいかまたはでないときは新規生成する
	 * @param width
	 * @param height
	 * @return 読み取ったピクセルデータの入ったByteBuffer, orderはLITTLE_ENDIAN
	 */
	public static ByteBuffer glReadPixels(
		@Nullable final ByteBuffer buffer,
		@IntRange(from=1) final int width, @IntRange(from=1) final int height) {

		ByteBuffer buf = buffer;
		if ((buf == null) || (buf.capacity() < width * height * 4)) {
			if (DEBUG) Log.v(TAG, "glReadPixels:allocate direct bytebuffer");
			buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
		}
		if ((buf.order() != ByteOrder.LITTLE_ENDIAN)) {
			buf.order(ByteOrder.LITTLE_ENDIAN);
		}
		buf.clear();
		// XXX GL|ES3の時はPBOとglMapBufferRange/glUnmapBufferを使うようにする?
		GLES20.glReadPixels(0, 0, width, height,
			GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
		buf.flip();

		return buf;
	}

}
