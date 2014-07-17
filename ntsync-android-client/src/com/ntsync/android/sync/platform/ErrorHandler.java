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

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.ExceptionHandler;
import android.content.Context;

/**
 * Initialize the HockeySDK for Crash Reporting
 */
public final class ErrorHandler {

	private static final Object INITLOCK = new Object();
	private static boolean initialized = false;

	private ErrorHandler() {
	}

	/**
	 * Init ErrorHandler, is initialized only one time.
	 * 
	 * @param context
	 */
	static void initErrorHandler(Context context) {
		synchronized (INITLOCK) {
			if (!initialized) {
				initialized = true;
				CrashManager.register(context,
						"96a040ec387e98f31c61358f01722ddc", new CListener());
			}
		}
	}

	/**
	 * Save an Exception for Reporting to CrashManager
	 * 
	 * @param ex
	 */
	public static void reportException(Throwable ex) {
		if (ex != null) {
			ExceptionHandler.saveException(ex, new CListener());
		}
	}

	/**
	 * Settings for HockeyApp. Crashed are send automatically
	 */
	private static class CListener extends CrashManagerListener {
		@Override
		public boolean ignoreDefaultHandler() {
			return true;
		}

		@Override
		public boolean shouldAutoUploadCrashes() {
			return true;
		}

		@Override
		public String getContact() {
			return "";
		}

		@Override
		public String getDescription() {
			return "";
		}

		@Override
		public String getUserID() {
			return "";
		}
	}
}
