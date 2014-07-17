package com.ntsync.android.sync.activities;

/*
 * Copyright (C) 2014 Markus Grieder
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;

import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.SyncUtils;

/**
 * Do Actions when Network-State changes. Currently:
 * <ul>
 * <li>Verificate pending Payments</li>
 * </ul>
 * 
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

	private static final String TAG = "NetworkChangeReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		SystemHelper.initSystem(context);

		Bundle extras = intent.getExtras();
		if (extras != null) {
			boolean noConnection = extras.getBoolean(
					ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			if (noConnection) {
				return;
			}
			if (SyncUtils.isNetworkConnected(context)) {
				checkForPaymentVerification(context);
			}
		}
	}

	/**
	 * Checks for a pending PaymentVerification
	 */
	private void checkForPaymentVerification(Context context) {
		if (SyncUtils.hasPaymentData(context)) {
			// Start Service
			Intent verifService = new Intent(context,
					PaymentVerificationService.class);
			context.startService(verifService);
			LogHelper.logD(TAG, "Start PaymentVerificationService", null);
		}
	}
}