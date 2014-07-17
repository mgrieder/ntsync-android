
package com.ntsync.android.sync.syncadapter;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on SyncService.java from the SampleSyncAdapter-Example in Android SDK
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

import com.ntsync.android.sync.platform.SystemHelper;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {

	private static final Object SYCN_ADAPTOR_LOCK = new Object();

	private static SyncAdapter syncAdapter = null;

	@Override
	public void onCreate() {
		super.onCreate();
		SystemHelper.initSystem(this);		
		synchronized (SYCN_ADAPTOR_LOCK) {
			if (syncAdapter == null) {
				syncAdapter = new SyncAdapter(getApplicationContext(), true);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		synchronized (SYCN_ADAPTOR_LOCK) {
			return syncAdapter.getSyncAdapterBinder();
		}
	}
}
