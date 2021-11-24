package com.serenegiant.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;

import java.util.Collections;
import java.util.Set;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.fragment.app.Fragment;

/**
 * Created by saki on 16/08/30.
 * Bluetooth機器の探索・接続・接続待ち・通信処理を行うためのヘルパークラス・メソッド
 * 	<uses-permission android:name="android.permission.BLUETOOTH" />
 *	<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * のパーミッションが必要
 */
public class BluetoothUtils {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static final String TAG = BluetoothUtils.class.getSimpleName();

	public interface BluetoothEnableCallback {
		public void onChanged(final boolean enabled);
	}

	@NonNull
	private final ActivityResultLauncher<Intent> mLauncher;

	/**
	 * コンストラクタ
	 * @param activity
	 * @param callback
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public BluetoothUtils(
		@NonNull final ComponentActivity activity,
		@NonNull final BluetoothEnableCallback callback) {

		mLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				callback.onChanged(isEnabled());
			});
	}

	/**
	 * コンストラクタ
	 * @param fragment
	 * @param callback
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public BluetoothUtils(
		@NonNull final Fragment fragment,
		@NonNull final BluetoothEnableCallback callback) {

		mLauncher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				callback.onChanged(isEnabled());
			});
	}

	/**
	 * Bluetoothが無効の場合に有効にするように要求する
	 * @return true: Bluetoothが有効, false: Bluetoothが無効
	 */
	public boolean requestEnable() {
		if (isAvailable() && !isEnabled()) {
			mLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
		}
		return isEnabled();
	}

	private static final String ACTION_REQUEST_DISABLE = "android.bluetooth.adapter.action.REQUEST_DISABLE";
	/**
	 * Bluetoothが有効な場合に無効にするように要求する
	 * hideなので動かない端末があるかも？
	 * @return true: Bluetoothが有効, false: Bluetoothが無効
	 */
	public boolean requestDisable() {
		if (isEnabled()) {
			mLauncher.launch(new Intent(ACTION_REQUEST_DISABLE));
		}
		return isEnabled();
	}
//--------------------------------------------------------------------------------
	/**
	 * 端末がBluetoothに対応しているかどうかを確認
	 * @return true Bluetoothに対応している
	 */
	public static boolean isAvailable() {
		try {
			final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			return adapter != null;
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
		return false;
	}

	/**
	 * 端末がBluetoothに対応していてBluetoothが有効になっているかどうかを確認
	 * パーミッションがなければfalse
	 * @return true Bluetoothが有効
	 */
	@SuppressLint("MissingPermission")
	public static boolean isEnabled() {
		try {
			final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			return adapter != null && adapter.isEnabled();	// #isEnabledはBLUETOOTHパーミッションがないとSecurityExceptionを投げる
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
		return false;
	}

	/**
	 * ペアリング済みのBluetooth機器一覧を取得する
	 * @return Bluetoothに対応していないまたは無効なら空set
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	@NonNull
	public static Set<BluetoothDevice> getBondedDevices() {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		return ((adapter != null) && adapter.isEnabled()) ? adapter.getBondedDevices() : Collections.emptySet();
	}

	/**
	 * 他の機器から探索可能になるように要求する
	 * bluetoothに対応していないか無効になっている時はIllegalStateException例外を投げる
	 * @param activity
	 * @param duration 探索可能時間[秒]
	 * @return 既に探索可能であればtrue
	 * @throws IllegalStateException
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public static boolean requestDiscoverable(@NonNull final Activity activity, final int duration) throws IllegalStateException {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if ((adapter == null) || !adapter.isEnabled()) {
			throw new IllegalStateException("bluetoothに対応していないか無効になっている");
		}
		if (adapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
			activity.startActivity(intent);
		}
		return adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
	}

	/**
	 * 他の機器から探索可能になるように要求する
	 * bluetoothに対応していないか無効になっている時はIllegalStateException例外を投げる
	 * @param fragment
	 * @param duration 0以下ならデフォルトの探索可能時間で120秒、 最大300秒まで設定できる
	 * @return
	 * @throws IllegalStateException
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public static boolean requestDiscoverable(@NonNull final Fragment fragment, final int duration) throws IllegalStateException {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if ((adapter == null) || !adapter.isEnabled()) {
			throw new IllegalStateException("bluetoothに対応していないか無効になっている");
		}
		if (adapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			if ((duration > 0) && (duration <= 300)) {
				intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
			}
			fragment.startActivity(intent);
		}
		return adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
	}

//--------------------------------------------------------------------------------
	/**
	 * 端末がBluetoothに対応しているが無効になっていれば有効にするように要求する
	 * 前もってbluetoothAvailableで対応しているかどうかをチェックしておく
	 * Bluetoothを有効にするように要求した時は#onActivityResultメソッドで結果を受け取る
	 * 有効にできればRESULT_OK, ユーザーがキャンセルするなどして有効に出来なければRESULT_CANCELEDが返る
	 * @param activity
	 * @param requestCode
	 * @return true Bluetoothに対応していて既に有効になっている
	 * @throws SecurityException パーミッションがなければSecurityExceptionが投げられる
	 * @deprecated #registerForActivityResult/#ActivityResultLauncherを使うように変更したのでそっちを使うこと
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	@Deprecated
	public static boolean requestBluetoothEnable(@NonNull final Activity activity, final int requestCode) throws SecurityException {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if ((adapter != null) && !adapter.isEnabled()) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			activity.startActivityForResult(intent, requestCode);
		}
		return adapter != null && adapter.isEnabled();
	}

	/**
	 * 端末がBluetoothに対応しているが無効になっていれば有効にするように要求する
	 * 前もってbluetoothAvailableで対応しているかどうかをチェックしておく
	 * Bluetoothを有効にするように要求した時は#onActivityResultメソッドで結果を受け取る
	 * 有効にできればRESULT_OK, ユーザーがキャンセルするなどして有効に出来なければRESULT_CANCELEDが返る
	 * @param fragment
	 * @param requestCode
	 * @return true Bluetoothに対応していて既に有効になっている
	 * @throws SecurityException パーミッションがなければSecurityExceptionが投げられる
	 * @deprecated #registerForActivityResult/#ActivityResultLauncherを使うように変更したのでそっちを使うこと
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	@Deprecated
	public static boolean requestBluetoothEnable(@NonNull final android.app.Fragment fragment, final int requestCode) throws SecurityException {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if ((adapter != null) && !adapter.isEnabled()) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			fragment.startActivityForResult(intent, requestCode);
		}
		return adapter != null && adapter.isEnabled();
	}

	/**
	 * 端末がBluetoothに対応しているが無効になっていれば有効にするように要求する
	 * 前もってbluetoothAvailableで対応しているかどうかをチェックしておく
	 * Bluetoothを有効にするように要求した時は#onActivityResultメソッドで結果を受け取る
	 * 有効にできればRESULT_OK, ユーザーがキャンセルするなどして有効に出来なければRESULT_CANCELEDが返る
	 * @param fragment
	 * @param requestCode
	 * @return true Bluetoothに対応していて既に有効になっている
	 * @throws SecurityException パーミッションがなければSecurityExceptionが投げられる
	 * @deprecated #registerForActivityResult/#ActivityResultLauncherを使うように変更したのでそっちを使うこと
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	@Deprecated
	public static boolean requestBluetoothEnable(@NonNull final Fragment fragment, final int requestCode) throws SecurityException {
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if ((adapter != null) && !adapter.isEnabled()) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			fragment.startActivityForResult(intent, requestCode);
		}
		return adapter != null && adapter.isEnabled();
	}

}