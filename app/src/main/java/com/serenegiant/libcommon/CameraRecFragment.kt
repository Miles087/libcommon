package com.serenegiant.libcommon

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.serenegiant.glpipeline.EncodePipeline
import com.serenegiant.glpipeline.IPipeline
import com.serenegiant.glutils.GLEffect
import com.serenegiant.media.*
import com.serenegiant.media.IRecorder.RecorderCallback
import com.serenegiant.mediastore.MediaStoreUtils
import com.serenegiant.system.BuildCheck
import com.serenegiant.utils.FileUtils
import com.serenegiant.widget.EffectCameraGLSurfaceView
import com.serenegiant.widget.IPipelineView
import com.serenegiant.widget.SimpleVideoSourceCameraTextureView
import java.io.IOException

/**
 * MediaAVRecorderを使ったカメラ映像の録画テスト用Fragment
 * このテストFragmentではアプリ内で直接録画しているがCameraFragmentや
 * EffectCameraFragmentの様に録画処理部分はサービスとして実行するように
 * した方がアプリ終了時に再生できない動画ファイルができてしまうのを防止できる。
 */
class CameraRecFragment : AbstractCameraFragment() {

	private var mEncoderSurface: Surface? = null
	private var mAudioSampler: IAudioSampler? = null
	private var mRecorder: IRecorder? = null
	@Volatile
	private var mVideoEncoder: IVideoEncoder? = null
	@Volatile
	private var mAudioEncoder: IAudioEncoder? = null

	override fun onLongClick(view: View): Boolean {
		if (DEBUG) Log.v(TAG, "onLongClick:${view}")
		if (mCameraView is EffectCameraGLSurfaceView) {
			val v = view as EffectCameraGLSurfaceView
			v.effect = (v.effect + 1) % GLEffect.EFFECT_NUM
			return true
		} else if (mCameraView is SimpleVideoSourceCameraTextureView) {
			val v = view as SimpleVideoSourceCameraTextureView
			if (v.isEffectSupported()) {
				v.effect = (v.effect + 1) % GLEffect.EFFECT_NUM
				return true
			}
		}
		return false
	}

	override fun isRecording(): Boolean {
		return mRecorder != null
	}

	@Throws(IOException::class)
	override fun internalStartRecording() {
		if (DEBUG) Log.v(TAG, "internalStartRecording:")
		val context: Context = requireContext()
		val outputFile: DocumentFile?
		= if (BuildCheck.isAPI29()) {
			// API29以降は対象範囲別ストレージ
			MediaStoreUtils.getContentDocument(
				context, "video/mp4",
				Environment.DIRECTORY_MOVIES + "/" + Const.APP_DIR,
				FileUtils.getDateTimeString() + ".mp4", null)
		} else {
			// ここの#getRecordingRoot呼び出しと#getRecordingFile呼び出しは等価
//			val dir = MediaFileUtils.getRecordingRoot(
//				context, Environment.DIRECTORY_MOVIES, Const.REQUEST_ACCESS_SD)
//			dir!!.createFile("video/mp4", FileUtils.getDateTimeString() + ".mp4")
			MediaFileUtils.getRecordingFile(
				context, Const.REQUEST_ACCESS_SD, Environment.DIRECTORY_MOVIES, "video/mp4",".mp4")
		}
		if (DEBUG) Log.v(TAG, "internalStartRecording:output=$outputFile," + outputFile?.uri)
		if (outputFile != null) {
			startEncoder(outputFile, 2, CHANNEL_COUNT, false)
		} else {
			throw IOException("could not access storage")
		}
		if (DEBUG) Log.v(TAG, "internalStartRecording:finished")
	}

	override fun internalStopRecording() {
		if (DEBUG) Log.v(TAG, "internalStopRecording:")
		stopEncoder()
		val recorder = mRecorder
		recorder?.stopRecording()
		// you should not wait and should not clear mRecorder here
		if (DEBUG) Log.v(TAG, "internalStopRecording:finished")
	}

	override fun onFrameAvailable() {
		val recorder = mRecorder
		recorder?.frameAvailableSoon()
	}

	override fun isRecordingSupported(): Boolean {
		return super.isRecordingSupported()
			|| (enablePipelineEncode && (mCameraView is IPipelineView))
	}

//--------------------------------------------------------------------------------
	//--------------------------------------------------------------------------------
	/**
	 * create IRecorder instance for recording and prepare, start
	 * @param outputFile
	 * @param audioSource
	 * @param audioChannels
	 * @param align16
	 */
	@Throws(IOException::class)
	private fun startEncoder(outputFile: DocumentFile,
		audioSource: Int, audioChannels: Int, align16: Boolean) {

		var recorder = mRecorder
		if (DEBUG) Log.d(TAG, "startEncoder:recorder=$recorder")
		if (recorder == null) {
			try {
				recorder = createRecorder(outputFile,
					audioSource, audioChannels, align16)
				recorder.prepare()
				recorder.startRecording()
				mRecorder = recorder
			} catch (e: Exception) {
				Log.w(TAG, "startEncoder:", e)
				stopEncoder()
				recorder?.stopRecording()
				mRecorder = null
				throw e
			}
		}
		if (DEBUG) Log.v(TAG, "startEncoder:finished")
	}

	private fun stopEncoder() {
		if (DEBUG) Log.v(TAG, "stopEncoder:")
		if (mEncoderSurface != null) {
			removeSurface(mEncoderSurface)
			mEncoderSurface = null
		}
		if (mVideoEncoder is IPipeline) {
			if (DEBUG) Log.v(TAG, "stopEncoder:remove Encoder from pipeline chains,${mVideoEncoder}")
			val pipeline = mVideoEncoder as IPipeline
			pipeline.remove()
		}
		mVideoEncoder = null
		mAudioEncoder = null
		if (mAudioSampler != null) {
			mAudioSampler!!.release()
			mAudioSampler = null
		}
		if (DEBUG) Log.v(TAG, "stopEncoder:finished")
	}

	/**
	 * create recorder and related encoder
	 * @param outputFile
	 * @param audio_source
	 * @param audio_channels
	 * @param align16
	 * @return
	 * @throws IOException
	 */
	@Throws(IOException::class)
	private fun createRecorder(outputFile: DocumentFile,
								 audio_source: Int, audio_channels: Int,
								 align16: Boolean): Recorder {
		if (DEBUG) Log.v(TAG, "createRecorder:basePath=" + outputFile.uri)
		val recorder = MediaAVRecorder(
			requireContext(), mRecorderCallback, outputFile)
		// create encoder for video recording
		mVideoEncoder = if (enablePipelineEncode && (mCameraView is IPipelineView)) {
			if (DEBUG) Log.v(TAG, "createRecorder:create EncoderPipeline")
			val view = mCameraView as IPipelineView
			val pipeline = EncodePipeline(view.getGLManager(), recorder, mEncoderListener) // API>=18
			view.addPipeline(pipeline)
			pipeline
		} else {
			if (DEBUG) Log.v(TAG, "createRecorder:create SurfaceEncoder")
			SurfaceEncoder(recorder, mEncoderListener) // API>=18
		}
		mVideoEncoder!!.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
		if (mVideoEncoder is AbstractVideoEncoder) {
			(mVideoEncoder as AbstractVideoEncoder).setVideoConfig(-1, 30, 10)
		}
		if (audio_source >= 0) {
			mAudioSampler = AudioSampler(audio_source,
				audio_channels, SAMPLE_RATE,
				AbstractAudioEncoder.SAMPLES_PER_FRAME,
				AbstractAudioEncoder.FRAMES_PER_BUFFER)
			mAudioSampler!!.start()
			mAudioEncoder = AudioSamplerEncoder(recorder, mEncoderListener, 2, mAudioSampler)
		}
		if (DEBUG) Log.v(TAG, "createRecorder:finished")
		return recorder
	}

	private val mRecorderCallback: RecorderCallback = object : RecorderCallback {
		override fun onPrepared(recorder: IRecorder) {
			if (DEBUG) Log.v(TAG, "mRecorderCallback#onPrepared:" + recorder
				+ ",mEncoderSurface=" + mEncoderSurface)
			val encoder = recorder.videoEncoder
			try {
				if (encoder is SurfaceEncoder) {
					if (mEncoderSurface == null) {
						val surface = recorder.inputSurface
						if (surface != null) {
							if (DEBUG) Log.v(TAG, "use SurfaceEncoder")
							mEncoderSurface = surface
							try {
								addSurface(surface)
							} catch (e: Exception) {
								mEncoderSurface = null
								throw e
							}
						}
					}
				} else if (encoder is EncodePipeline) {
					if (DEBUG) Log.v(TAG, "use EncodePipeline")
					mEncoderSurface = null
				} else if (encoder is IVideoEncoder) {
					mEncoderSurface = null
					throw RuntimeException("unknown video encoder $encoder")
				}
			} catch (e: Exception) {
				if (DEBUG) Log.w(TAG, e)
				try {
					stopRecording()
				} catch (e1: Exception) {
					// unrecoverable exception
					Log.w(TAG, e1)
				}
			}
			if (DEBUG) Log.v(TAG, "mRecorderCallback#onPrepared:finished")
		}

		override fun onStarted(recorder: IRecorder) {
			if (DEBUG) Log.v(TAG, "mRecorderCallback#onStarted:$recorder")
		}

		override fun onStopped(unused: IRecorder) {
			if (DEBUG) Log.v(TAG, "mRecorderCallback#onStopped:$unused")
			stopEncoder()
			val recorder = mRecorder
			mRecorder = null
			try {
				queueEvent({
					if (recorder != null) {
						try {
							recorder.release()
						} catch (e: Exception) {
							Log.w(TAG, e)
						}
					}
				}, 1000)
			} catch (e: IllegalStateException) {
				// ignore, will be already released
				Log.w(TAG, e)
			}
			clearRecordingState()
			if (DEBUG) Log.v(TAG, "mRecorderCallback#onStopped:finished")
		}

		override fun onError(e: Exception) {
			Log.w(TAG, e)
			val recorder = mRecorder
			mRecorder = null
			if (recorder != null && (recorder.isReady || recorder.isStarted)) {
				recorder.stopRecording()
			}
		}
	}

	private val mEncoderListener: EncoderListener = object : EncoderListener {
		override fun onStartEncode(encoder: Encoder, source: Surface?,
								   captureFormat: Int, mayFail: Boolean) {
			if (DEBUG) Log.v(TAG, "mEncoderListener#onStartEncode:$encoder")
		}

		override fun onStopEncode(encoder: Encoder) {
			if (DEBUG) Log.v(TAG, "mEncoderListener#onStopEncode:$encoder")
		}

		override fun onDestroy(encoder: Encoder) {
			if (DEBUG) Log.v(TAG, "mEncoderListener#onDestroy:"
				+ encoder + ",mRecorder=" + mRecorder)
			if (DEBUG) Log.v(TAG, "mEncoderListener#onDestroy:finished")
		}

		override fun onError(e: Throwable) {
			Log.w(TAG, e)
			val recorder = mRecorder
			mRecorder = null
			if (recorder != null && (recorder.isReady || recorder.isStarted)) {
				recorder.stopRecording()
			}
		}
	}

	companion object {
		private const val DEBUG = true // TODO set false on release
		private val TAG = CameraRecFragment::class.java.simpleName

		fun newInstance(
			@LayoutRes layoutRes: Int, @StringRes titleRes: Int,
			pipelineMode: Int = IPipelineView.PREVIEW_ONLY,
			enablePipelineEncode: Boolean = false): CameraRecFragment {

			val fragment = CameraRecFragment()
			val args = Bundle()
			args.putInt(ARGS_KEY_LAYOUT_ID, layoutRes)
			args.putInt(ARGS_KEY_TITLE_ID, titleRes)
			args.putInt(ARGS_KEY_PIPELINE_MODE, pipelineMode)
			args.putBoolean(ARGS_KEY_ENABLE_PIPELINE_RECORD, enablePipelineEncode)
			fragment.arguments = args
			return fragment
		}
	}
}