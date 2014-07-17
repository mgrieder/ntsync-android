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

import com.ntsync.android.sync.shared.LogHelper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 */
public final class SystemHelper {

	private static final String TAG = "SystemHelper";

	private SystemHelper() {
	}

	/**
	 * Initialize all System-Services for our app. Does nothing if they are
	 * already initialized. Startup the LocalService for detecting Contact
	 * Changes.
	 * 
	 * @param ctx
	 */
	public static void initSystem(Context ctx) {
		initSystem(ctx, true);
	}

	/**
	 * Initialize all System-Services for our app. Does nothing if they are
	 * already initialized.
	 * 
	 * @param ctx
	 * @param startLocalService
	 *            true: Startup the LocalService for detecting Contact Changes.
	 */
	public static void initSystem(Context ctx, boolean startLocalService) {
		ErrorHandler.initErrorHandler(ctx);
		PRNGFixes.apply();
		if (startLocalService && !LocalService.isStarted()) {
			Intent intent = new Intent(ctx, LocalService.class);
			ctx.startService(intent);
		}
	}

	/**
	 * Get a Package-Version for a Context
	 * 
	 * @param context
	 *            null is not allowed
	 * @return Version or "n.a."
	 */
	public static String getPkgVersion(Context context) {
		String pkgVersion;
		try {
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(
					context.getPackageName(), 0);
			pkgVersion = pInfo.versionName;
		} catch (NameNotFoundException e) {
			LogHelper.logI(TAG, "PackageName not found.", e);
			pkgVersion = "n.a";
		}
		return pkgVersion;
	}
}
