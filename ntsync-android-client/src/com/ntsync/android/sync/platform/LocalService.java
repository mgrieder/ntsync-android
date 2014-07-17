package com.ntsync.android.sync.platform;

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

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.IBinder;
import android.provider.ContactsContract.Data;
import android.util.Log;

import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.SyncUtils;

/**
 * Local Service which is listening for Contact Changes and updates their
 * modification time stamp. TODO Feature: This Local Service Could be replaced
 * for Android 4.3/4.4
 */
public class LocalService extends Service {

	private static final int WAIT_TIMEMS = 1000;
	private static final String TAG = "LocalService";
	private Thread thread;
	private DataChangeObserver observer;

	final Semaphore gate = new Semaphore(0, true);

	private static final AtomicBoolean STARTED = new AtomicBoolean();

	/**
	 * @return true if Service is started.
	 */
	public static boolean isStarted() {
		return STARTED.get();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		STARTED.set(true);
		SystemHelper.initSystem(this, false);

		thread = new Thread(new DataWorkerThread(), "DataChangeProcessor");
		thread.start();

		observer = new DataChangeObserver();
		this.getContentResolver().registerContentObserver(Data.CONTENT_URI,
				true, observer);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "Received start id " + startId + ": " + intent);
		}
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (observer != null) {
			this.getContentResolver().unregisterContentObserver(observer);
		}
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(WAIT_TIMEMS);
			} catch (InterruptedException e) {
				LogHelper.logI(TAG, "Wait for Thread Stop interrupted", e);
			}
		}
		STARTED.set(false);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private class DataWorkerThread implements Runnable {
		public void run() {
			try {
				while (true) {
					gate.acquire();
					// Eat all Permits because our method handle all
					// notifications.
					gate.drainPermits();
					try {
						SyncUtils.stopProcessDataChanges();
						ContactManager.checkForModifications(LocalService.this);
					} finally {
						SyncUtils.startProcessDataChanges();
					}
				}
			} catch (InterruptedException ex) {
				LogHelper.logI(TAG, "DataWorkerThread stopped", ex);
			}
		}
	}

	private class DataChangeObserver extends ContentObserver {

		public DataChangeObserver() {
			super(null);
		}

		@Override
		public void onChange(boolean selfChange) {
			// Only process when change was not from program code
			if (SyncUtils.processDataChanges()) {
				gate.release();
			}
		}
	}
}