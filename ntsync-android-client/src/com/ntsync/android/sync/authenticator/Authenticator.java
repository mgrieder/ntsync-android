package com.ntsync.android.sync.authenticator;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on Authenticator.java from the SampleSyncAdapter-Example in Android SDK
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

import static com.ntsync.android.sync.shared.LogHelper.logI;

import org.spongycastle.util.encoders.Base64;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.ntsync.android.sync.activities.AuthenticatorActivity;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.Constants;

/**
 * This class is an implementation of AbstractAccountAuthenticator for
 * authenticating accounts. The interesting thing that this class demonstrates
 * is the use of authTokens as part of the authentication process. In the
 * account setup UI, the user enters their username and password. But for our
 * subsequent calls off to the service for syncing, we want to use an authtoken
 * instead - so we're not continually sending the password over the wire.
 * getAuthToken() will be called when SyncAdapter calls
 * AccountManager.blockingGetAuthToken(). When we get called, we need to return
 * the appropriate authToken for the specified account. If we already have an
 * authToken stored in the account, we return that authToken. If we don't, but
 * we do have a username and password, then we'll attempt to talk to the service
 * to fetch an authToken. If that fails (or we didn't have a username/password),
 * then we need to prompt the user - so we create an AuthenticatorActivity
 * intent and return that. That will display the dialog that prompts the user
 * for their login information.
 */
class Authenticator extends AbstractAccountAuthenticator {

	private static final String AUTH_FAILED_MSG = "Authentification failed";

	/** The tag used to log to adb console. **/
	private static final String TAG = "Authenticator";

	// Authentication Service context
	private final Context mContext;

	public Authenticator(Context context) {
		super(context);
		mContext = context;
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response,
			String accountType, String authTokenType,
			String[] requiredFeatures, Bundle options) {
		final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
				response);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response,
			Account account, Bundle options) {
		return null;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response,
			String accountType) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle loginOptions)
			throws NetworkErrorException {

		// If the caller requested an authToken type we don't support, then
		// return an error
		if (!authTokenType.equals(Constants.AUTHTOKEN_TYPE)) {
			throw new IllegalArgumentException("invalid authTokenType");
		}

		// Extract the username and password from the Account Manager, and ask
		// the server for an appropriate AuthToken.
		final AccountManager am = AccountManager.get(mContext);
		final String password = am.getPassword(account);
		if (password != null) {
			final String authToken;
			try {
				byte[] srpPassword = Base64.decode(password);
				authToken = NetworkUtilities.authenticate(mContext,
						account.name, srpPassword);
			} catch (ServerException e1) {
				final Bundle result = new Bundle();
				result.putInt(AccountManager.KEY_ERROR_CODE,
						Constants.AUTH_ERRORCODE_SERVEREXCEPTION);
				result.putString(AccountManager.KEY_ERROR_MESSAGE,
						e1.getMessage());
				logI(TAG, AUTH_FAILED_MSG, e1);
				return result;
			}
			if (!TextUtils.isEmpty(authToken)) {
				// Authentification ok
				final Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE,
						Constants.ACCOUNT_TYPE);
				result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
				return result;
			}
		}

		// prompt for credentials
		return startLoginView(response, account);
	}

	private Bundle startLoginView(AccountAuthenticatorResponse response,
			Account account) {
		final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
		intent.putExtra(AuthenticatorActivity.PARAM_USERNAME, account.name);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
				response);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		// null means we don't support multiple authToken types
		Log.v(TAG, "getAuthTokenLabel()");
		return null;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response,
			Account account, String[] features) {
		// This call is used to query whether the Authenticator supports
		// specific features. We don't expect to get called, so we always
		// return false (no) for any queries.
		Log.v(TAG, "hasFeatures()");
		final Bundle result = new Bundle();
		result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
		return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle loginOptions) {
		Log.v(TAG, "updateCredentials()");
		return null;
	}
}
