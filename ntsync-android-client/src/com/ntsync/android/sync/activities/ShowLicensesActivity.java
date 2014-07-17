package com.ntsync.android.sync.activities;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on SettingsLicenseActivity.java from the Android Settings App from Android
 *  
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>. 
 *
 *
 *
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"):
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.LogHelper;

/**
 * The "dialog" that shows from "License" Option.
 */
public class ShowLicensesActivity extends FragmentActivity implements
		LoaderCallbacks<String> {

	private static final String TAG = "SettingsLicenseActivity";

	private static final int LOADID_LICENSES = 1;

	private WebView mWebView;
	private ProgressDialog mSpinnerDlg;
	private AlertDialog mTextDlg;

	public ShowLicensesActivity() {
		super();
		mWebView = null;
		mSpinnerDlg = null;
		mTextDlg = null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SystemHelper.initSystem(this);
		mWebView = new WebView(this);

		CharSequence title = getText(R.string.license_activity_title);
		CharSequence msg = getText(R.string.license_activity_loading);

		ProgressDialog pd = ProgressDialog.show(this, title, msg, true, false);
		pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mSpinnerDlg = pd;

		getSupportLoaderManager().initLoader(LOADID_LICENSES, null, this);
	}

	@Override
	protected void onDestroy() {
		if (mTextDlg != null && mTextDlg.isShowing()) {
			mTextDlg.dismiss();
		}
		if (mSpinnerDlg != null && mSpinnerDlg.isShowing()) {
			mSpinnerDlg.dismiss();
		}
		super.onDestroy();
	}

	private void showPageOfText(String text) {
		// Create an AlertDialog to display the WebView in.
		AlertDialog.Builder builder = new AlertDialog.Builder(
				ShowLicensesActivity.this);
		builder.setCancelable(true).setView(mWebView)
				.setTitle(R.string.license_activity_title);

		mTextDlg = builder.create();
		mTextDlg.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dlgi) {
				ShowLicensesActivity.this.finish();
			}
		});

		// Begin the loading. This will be done in a separate thread in WebView.
		mWebView.loadDataWithBaseURL(null, text, "text/html", "utf-8", null);
		mWebView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				if (mSpinnerDlg != null) {
					mSpinnerDlg.dismiss();
				}
				if (!ShowLicensesActivity.this.isFinishing()) {
					mTextDlg.show();
				}
			}
		});

		mWebView = null;
	}

	private void showErrorAndFinish() {
		mSpinnerDlg.dismiss();
		mSpinnerDlg = null;
		Toast.makeText(this, R.string.license_activity_unavailable,
				Toast.LENGTH_LONG).show();
		finish();
	}

	public Loader<String> onCreateLoader(int id, Bundle args) {
		return new LicenseFileLoader(R.raw.lizenzen, this);
	}

	public void onLoadFinished(Loader<String> loader, String data) {
		if (!TextUtils.isEmpty(data)) {
			showPageOfText(data);
		} else {
			showErrorAndFinish();
		}
	}

	public void onLoaderReset(Loader<String> loader) {
		// ignore reset
	}

	private static class LicenseFileLoader extends AsyncTaskLoader<String> {

		public static final int STATUS_OK = 0;
		public static final int STATUS_NOT_FOUND = 1;
		public static final int STATUS_READ_ERROR = 2;

		private int rawResId;

		public LicenseFileLoader(int rawResId, Context context) {
			super(context);
			this.rawResId = rawResId;
		}

		@Override
		protected void onStartLoading() {
			forceLoad();
		}

		@Override
		protected void onStopLoading() {
			cancelLoad();
		}

		@Override
		public String loadInBackground() {
			int status = STATUS_OK;
			InputStreamReader inputReader = null;
			StringBuilder data = new StringBuilder(2048);
			try {
				char[] tmp = new char[2048];
				int numRead;
				inputReader = new InputStreamReader(getContext().getResources()
						.openRawResource(rawResId), "UTF-8");
				while ((numRead = inputReader.read(tmp)) >= 0) {
					data.append(tmp, 0, numRead);
				}
			} catch (FileNotFoundException e) {
				Log.e(TAG, "License HTML file not found.", e);
				status = STATUS_NOT_FOUND;
			} catch (IOException e) {
				Log.e(TAG, "Error reading license HTML file at ", e);
				status = STATUS_READ_ERROR;
			} finally {
				try {
					if (inputReader != null) {
						inputReader.close();
					}
				} catch (IOException e) {
					LogHelper.logW(TAG, "Closing license-file failed", e);
				}
			}

			if ((status == STATUS_OK) && TextUtils.isEmpty(data)) {
				Log.e(TAG, "License HTML is empty (from " + rawResId + ")");
			}
			return data.toString();
		}
	}
}