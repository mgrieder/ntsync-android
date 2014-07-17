package com.ntsync.android.sync.activities;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on AuthenticatorActivity.java from the SampleSyncAdapter-Example in Android SDK
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
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import javax.crypto.SecretKey;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.LoginProgressDialog.LoginDialogListener;
import com.ntsync.android.sync.activities.LoginProgressDialog.LoginError;
import com.ntsync.android.sync.client.ClientKeyHelper;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.SyncUtils;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends AbstractAuthenticatorActivity
		implements LoginDialogListener {
	/** The Intent flag to confirm credentials. */
	public static final String PARAM_CONFIRM_CREDENTIALS = "confirmCredentials";

	/** The Intent extra to store username. */
	public static final String PARAM_USERNAME = "username";

	/** The tag used to log to adb console. */
	private static final String TAG = "AuthenticatorActivity";
	private AccountManager mAccountManager;

	private static final int REGISTRATION_CODE = 1;

	private static final int ENTERKEY_CODE = 2;

	/**
	 * If set we are just checking that the user knows their credentials; this
	 * doesn't cause the user's password or authToken to be changed on the
	 * device.
	 */
	private Boolean mConfirmCredentials = false;

	private TextView mMessage;

	private EditText mPasswordEdit;

	/** Was the original caller asking for an entirely new account? */
	protected boolean mRequestNewAccount = false;

	private String mUsername;

	private EditText mUsernameEdit;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		SystemHelper.initSystem(this);
		mAccountManager = AccountManager.get(this);
		Log.i(TAG, "loading data from Intent");
		final Intent intent = getIntent();
		mUsername = intent.getStringExtra(PARAM_USERNAME);
		mRequestNewAccount = mUsername == null;
		mConfirmCredentials = intent.getBooleanExtra(PARAM_CONFIRM_CREDENTIALS,
				false);
		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "    request new: " + mRequestNewAccount);
		}
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.login_activity);
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
				R.drawable.login);
		mMessage = (TextView) findViewById(R.id.message);
		mUsernameEdit = (EditText) findViewById(R.id.username_edit);
		mPasswordEdit = (EditText) findViewById(R.id.password_edit);
		if (!TextUtils.isEmpty(mUsername)) {
			mUsernameEdit.setText(mUsername);
		}
		mMessage.setText(null);

	}

	/**
	 * Handles onClick event on the Submit button. Sends username/password to
	 * the server for authentication. The button is configured to call
	 * handleLogin() in the layout XML.
	 * 
	 * @param view
	 *            The Submit button for which this method is invoked
	 */
	public void handleLogin(View view) {
		if (mRequestNewAccount) {
			mUsername = mUsernameEdit.getText().toString();
		}
		String password = mPasswordEdit.getText().toString();

		boolean hasErrors = false;
		if (TextUtils.isEmpty(mUsername)) {
			// If no username, then we ask the user to log in using an
			// appropriate service.
			mUsernameEdit
					.setError(getText(R.string.login_activity_username_missing));
			hasErrors = true;
		}
		if (TextUtils.isEmpty(password)) {
			// We have an account but no password
			mPasswordEdit
					.setError(getText(R.string.login_activity_password_missing));
			hasErrors = true;
		}

		if (hasErrors) {
			mMessage.setText(getText(R.string.login_activity_loginfail_text_both));
		} else {
			mPasswordEdit.setError(null);
			mUsernameEdit.setError(null);
			mMessage.setText(null);

			// Show a progress dialog, and kick off a background task to perform
			// the user login attempt.
			LoginProgressDialog progressDialog = LoginProgressDialog
					.newInstance(mUsername, password);
			progressDialog.show(getSupportFragmentManager(), "LoginDialog");
		}
	}

	/**
	 * Handles onClick event for register
	 * 
	 * @param view
	 */
	public void handleRegister(View view) {
		// Start Register Action

		Intent intent = new Intent(this, RegisterActivity.class);
		startActivityForResult(intent, REGISTRATION_CODE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REGISTRATION_CODE) {
			if (resultCode == RESULT_OK) {
				String username = data
						.getStringExtra(RegisterActivity.PARAM_USERNAME);
				String password = data
						.getStringExtra(RegisterActivity.PARAM_PWD);

				mRequestNewAccount = true;
				mUsername = username;
				finishLogin(null, null, password);
			}
		} else if (requestCode == ENTERKEY_CODE) {
			if (resultCode == RESULT_OK) {
				successFinishActivity();
			} else if (resultCode == RESULT_CANCELED) {
				finish();
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Called when response is received from the server for confirm credentials
	 * request. See onAuthenticationResult(). Sets the
	 * AccountAuthenticatorResult which is sent back to the caller.
	 * 
	 * @param result
	 *            the confirmCredentials result.
	 */
	private void finishConfirmCredentials(boolean result, String password) {
		Log.i(TAG, "finishConfirmCredentials()");
		final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
		mAccountManager.setPassword(account, password);
		final Intent intent = new Intent();
		intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
		setAccountAuthenticatorResult(intent.getExtras());
		setResult(RESULT_OK, intent);
		finish();
	}

	/**
	 * Called when response is received from the server for authentication
	 * request. See onAuthenticationResult(). Sets the
	 * AccountAuthenticatorResult which is sent back to the caller. We store the
	 * authToken that's returned from the server as the 'password' for this
	 * account - so we're never storing the user's actual password locally.
	 * 
	 * @param authtoken
	 *            can be null
	 * 
	 * @param saltKeyCheck
	 * 
	 * @param result
	 *            the confirmCredentials result.
	 */
	private void finishLogin(String authtoken, byte[] saltKeyCheck,
			String password) {
		final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
		if (mRequestNewAccount) {
			if (authtoken != null) {
				mAccountManager.setAuthToken(account, Constants.AUTHTOKEN_TYPE,
						authtoken);
			}
			SyncUtils.createAccount(this, account, mAccountManager, password);
		} else {
			mAccountManager.setPassword(account, password);
		}

		SecretKey key = ClientKeyHelper.getPrivateKey(account, mAccountManager);
		if (key == null && authtoken != null && saltKeyCheck != null
				&& saltKeyCheck.length > ClientKeyHelper.SALT_LENGHT) {
			// Show Enter Key Dialog
			startEnterKeyView(account, authtoken, saltKeyCheck);
		} else {
			successFinishActivity();
		}
	}

	private void successFinishActivity() {
		final Intent intent = new Intent();
		intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
		intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		setAccountAuthenticatorResult(intent.getExtras());
		setResult(RESULT_OK, intent);
		finish();
	}

	private void startEnterKeyView(Account account, final String authToken,
			byte[] saltPwdCheck) {
		Intent intent = KeyPasswordActivity.createKeyPasswortActivity(this,
				account, authToken, saltPwdCheck);
		this.startActivityForResult(intent, ENTERKEY_CODE);
	}

	public void onCreateEnd(boolean success, boolean cancel, LoginError error,
			String authtoken, byte[] keySaltAndKeyCheck, byte[] srpPassword) {
		Log.i(TAG, "onAuthenticationResult(" + success + ")");

		if (success) {
			String password = Base64
					.encodeToString(srpPassword, Base64.DEFAULT);
			if (!mConfirmCredentials) {
				finishLogin(authtoken, keySaltAndKeyCheck, password);
			} else {
				finishConfirmCredentials(success, password);
			}
		} else {
			Log.e(TAG, "onCreateEnd: failed to authenticate");
			int msg;
			if (error != null) {
				switch (error) {
				case NETWORK_ERROR:
					msg = R.string.login_activity_networkerror;
					break;
				case SERVER_ERROR:
					msg = R.string.login_activity_servererror;
					break;
				default:
					if (mRequestNewAccount) {
						msg = R.string.login_activity_loginfail_text_both;
					} else {
						// when the account is already in the database but the
						// password doesn't work.
						msg = R.string.login_activity_loginfail_text_pwonly;
					}
					break;
				}
			} else {
				msg = R.string.login_activity_loginfail_text_both;
			}
			mMessage.setVisibility(View.VISIBLE);
			mMessage.setText(getText(msg));
		}
	}
}
