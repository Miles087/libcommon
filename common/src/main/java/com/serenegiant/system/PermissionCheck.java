package com.serenegiant.system;
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

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public final class PermissionCheck {
	private PermissionCheck() {
		// インスタンス化をエラーにするためにデフォルトコンストラクタをprivateに
	}

	public static final void dumpPermissions(@Nullable final Context context) {
    	if (context == null) return;
		try {
			final PackageManager pm = context.getPackageManager();
			final List<PermissionGroupInfo> list = pm.getAllPermissionGroups(PackageManager.GET_META_DATA);
			for (final PermissionGroupInfo info : list) {
				Log.d("PermissionCheck", info.name);
			}
		} catch (final Exception e) {
			Log.w("", e);
		}
	}

	/**
	 * パーミッションを確認
	 * @param context
	 * @param permissionName
	 * @return
	 */
	@SuppressLint("NewApi")
	public static int checkSelfPermission(@Nullable final Context context, final String permissionName) {
		if (context == null) return PackageManager.PERMISSION_DENIED;
		int result = PackageManager.PERMISSION_DENIED;
		try {
			result = ContextCompat.checkSelfPermission(context, permissionName);
		} catch (final Exception e) {
			Log.w("", e);
		}
		return result;
	}

	/**
	 * パーミッションを確認
	 * @param context
	 * @param permissionName
	 * @return 指定したパーミッションがあればtrue
	 */
	@SuppressLint("NewApi")
	public static boolean hasPermission(@Nullable final Context context, final String permissionName) {
    	if (context == null) return false;
		boolean result = false;
		try {
			result = ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED;
		} catch (final Exception e) {
			Log.w("", e);
		}
    	return result;
    }

	/**
	 * 指定した複数のパーミッションを確認して保持しているパーミッション配列を返す
	 * @param context
	 * @param permissions
	 * @return
	 */
	@NonNull
	public static String[] hasPermission(
		@Nullable final Context context,
		@NonNull final String[] permissions) {

		final ArrayList<String> result = new ArrayList<>();
		if (context != null) {
			for (final String permission: permissions) {
				if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
					result.add(permission);
				}
			}
		}
		return result.toArray(new String[0]);
	}

    /**
     * 録音のミッションがあるかどうかを確認
     * @param context
     * @return 録音のパーミッションがあればtrue
     */
    public static boolean hasAudio(@Nullable final Context context) {
    	return hasPermission(context, permission.RECORD_AUDIO);
    }

    /**
     * ネットワークへのアクセスパーミッション(INTERNET)があるかどうかを確認
     * @param context
     * @return ネットワークへのアクセスパーミッションがあればtrue
     */
    public static boolean hasNetwork(@Nullable final Context context) {
    	return hasPermission(context, permission.INTERNET);
    }

	/**
	 * ネットワーク接続状態へのアクセスパーミッション(ACCESS_NETWORK_STATE)があるかどうかを確認
	 * @param context
	 * @return ネットワーク接続状態へのアクセスパーミッションがあればtrue
	 */
	public static boolean hasNetworkState(@Nullable final Context context) {
		return hasPermission(context, permission.ACCESS_NETWORK_STATE);
	}

    /**
     * 外部ストレージへの書き込みパーミッションがあるかどうかを確認
     * @param context
     * @return 外部ストレージへの書き込みパーミッションがあればtrue
     */
    public static boolean hasWriteExternalStorage(@Nullable final Context context) {
    	return hasPermission(context, permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * 外部ストレージからの読み込みパーミッションがあるかどうかを確認
     * @param context
     * @return 外部ストレージへの読み込みパーミッションがあればtrue
     */
    @SuppressLint("InlinedApi")
	public static boolean hasReadExternalStorage(@Nullable final Context context) {
    	if (BuildCheck.isAndroid4())
    		return hasPermission(context, permission.READ_EXTERNAL_STORAGE);
    	else
    		return hasPermission(context, permission.WRITE_EXTERNAL_STORAGE);
    }

	/**
	 * 位置情報アクセスのパーミッションが有るかどうかを確認
	 * @param context
	 * @return
	 */
	public static boolean hasAccessLocation(@Nullable final Context context) {
		return hasPermission(context, permission.ACCESS_COARSE_LOCATION)
			&& hasPermission(context, permission.ACCESS_FINE_LOCATION);
	}

	/**
	 * 低精度位置情報アクセスのパーミッションが有るかどうかを確認
	 * @param context
	 * @return
	 */
	public static boolean hasAccessCoarseLocation(@Nullable final Context context) {
		return hasPermission(context, permission.ACCESS_COARSE_LOCATION);
	}

	/**
	 * 高精度位置情報アクセスのパーミッションが有るかどうかを確認
	 * @param context
	 * @return
	 */
	public static boolean hasAccessFineLocation(@Nullable final Context context) {
		return hasPermission(context, permission.ACCESS_FINE_LOCATION);
	}

	/**
	 * カメラへアクセス可能かどうか
	 * @param context
	 * @return
	 */
	public static boolean hasCamera(@Nullable final Context context) {
		return hasPermission(context, permission.CAMERA);
	}

	/**
	 * アプリの詳細設定へ遷移させる(パーミッションを取得できなかった時など)
	 * @param context
	 */
	public static void openSettings(@NonNull final Context context) {
	    final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
	    final Uri uri = Uri.fromParts("package", context.getPackageName(), null);
	    intent.setData(uri);
	    context.startActivity(intent);
	}

	/**
	 * AndroidManifest.xmlに設定されているはずのパーミッションをチェックする
	 * @param context
	 * @param expectations
	 * @return 空リストなら全てのパーミッションが入っていた,
	 * @throws IllegalArgumentException
	 * @throws PackageManager.NameNotFoundException
	 */
	@NonNull
	public static List<String> missingPermissions(
		@NonNull final Context context,
		@NonNull final String[] expectations)
			throws IllegalArgumentException, PackageManager.NameNotFoundException {

	    return missingPermissions(context, Arrays.asList(expectations));
	}

	/**
	 * AndroidManifest.xmlに設定されているはずのパーミッションをチェックする
	 * @param context
	 * @param expectations
	 * @return 空リストなら全てのパーミッションが入っていた,
	 * @throws IllegalArgumentException
	 * @throws PackageManager.NameNotFoundException
	 */
	@NonNull
	public static List<String> missingPermissions(
		@NonNull final Context context,
		@NonNull final Collection<String> expectations)
			throws IllegalArgumentException, PackageManager.NameNotFoundException {

		final PackageManager pm = context.getPackageManager();
		final PackageInfo pi = pm.getPackageInfo(
			context.getPackageName(), PackageManager.GET_PERMISSIONS);
		final List<String> result = new ArrayList<>(expectations);
		final String[] info = pi.requestedPermissions;
		if (info != null) {
			for (String i : info) {
				result.remove(i);
			}
		}
		return result;
	}
}
