package com.ntsync.android.sync.authenticator;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on AuthenticatorService.java from the SampleSyncAdapter-Example in Android SDK
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.ntsync.android.sync.platform.SystemHelper;

/**
 * Service for Account Management.
 */
public class AuthenticationService extends Service {

	private static final String TAG = "AuthenticationService";

	private Authenticator mAuthenticator;

	@Override
	public void onCreate() {		
		super.onCreate();
		SystemHelper.initSystem(this);				
		if (Log.isLoggable(TAG, Log.VERBOSE)) {
			Log.v(TAG, "AuthenticationService Authentication Service started.");
		}
		mAuthenticator = new Authenticator(this);			
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Log.isLoggable(TAG, Log.VERBOSE)) {
			Log.v(TAG, "AuthenticationService Authentication Service stopped.");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (Log.isLoggable(TAG, Log.VERBOSE)) {
			Log.v(TAG,
					"getBinder()...  returning the AccountAuthenticator binder for intent "
							+ intent);
		}
		return mAuthenticator.getIBinder();
	}
}
