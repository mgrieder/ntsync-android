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
 *
 *
 *
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

/**
 * Base class for implementing an Activity that is used to help implement an
 * AbstractAccountAuthenticator. If the AbstractAccountAuthenticator needs to
 * use an activity to handle the request then it can have the activity extend
 * AccountAuthenticatorActivity. The AbstractAccountAuthenticator passes in the
 * response to the intent using the following:
 * 
 * <pre>
 *      intent.putExtra({@link AccountManager#KEY_ACCOUNT_AUTHENTICATOR_RESPONSE}, response);
 * </pre>
 * 
 * The activity then sets the result that is to be handed to the response via
 * {@link #setAccountAuthenticatorResult(android.os.Bundle)}. This result will
 * be sent as the result of the request when the activity finishes. If this is
 * never set or if it is set to null then error
 * {@link AccountManager#ERROR_CODE_CANCELED} will be called on the response.
 * 
 * mg: Is the Default-Class AccountAuthenticatorActivity, but changed to use
 * FragmentActivity
 */
public abstract class AbstractAuthenticatorActivity extends FragmentActivity {
	private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
	private Bundle mResultBundle = null;

	/**
	 * Set the result that is to be sent as the result of the request that
	 * caused this Activity to be launched. If result is null or this method is
	 * never called then the request will be canceled.
	 * 
	 * @param result
	 *            this is returned as the result of the
	 *            AbstractAccountAuthenticator request
	 */
	public final void setAccountAuthenticatorResult(Bundle result) {
		mResultBundle = result;
	}

	/**
	 * Retreives the AccountAuthenticatorResponse from either the intent of the
	 * icicle, if the icicle is non-zero.
	 * 
	 * @param icicle
	 *            the save instance data of this Activity, may be null
	 */
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		mAccountAuthenticatorResponse = getIntent().getParcelableExtra(
				AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

		if (mAccountAuthenticatorResponse != null) {
			mAccountAuthenticatorResponse.onRequestContinued();
		}
	}

	/**
	 * Sends the result or a Constants.ERROR_CODE_CANCELED error if a result
	 * isn't present.
	 */
	@Override
	public void finish() {
		if (mAccountAuthenticatorResponse != null) {
			// send the result bundle back if set, otherwise send an error.
			if (mResultBundle != null) {
				mAccountAuthenticatorResponse.onResult(mResultBundle);
			} else {
				mAccountAuthenticatorResponse.onError(
						AccountManager.ERROR_CODE_CANCELED, "canceled");
			}
			mAccountAuthenticatorResponse = null;
		}
		super.finish();
	}
}
