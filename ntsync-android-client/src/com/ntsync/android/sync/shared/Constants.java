package com.ntsync.android.sync.shared;

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

import com.ntsync.android.sync.BuildConfig;
import com.ntsync.android.sync.R;

public final class Constants {

	private Constants() {

	}

	/** Ob im Debug-Modus die Release-Configuration verwendet soll. */
	public static final boolean DEBUG_RELEASE_CONFIG = false;

	/** True if a Release-Configuration should be used. */
	public static final boolean USE_RELEASE_CONFIG = !BuildConfig.DEBUG
			|| DEBUG_RELEASE_CONFIG;

	/**
	 * Account type string.
	 */
	public static final String ACCOUNT_TYPE = "com.ntsync.android.sync";

	public static final int AUTH_ERRORCODE_SERVEREXCEPTION = 1001;

	/**
	 * Authtoken type string.
	 */
	public static final String AUTHTOKEN_TYPE = "com.ntsync.android.sync";

	public static final String CONTACT_AUTHORITY = "com.android.contacts";

	public static final int NOTIF_PHOTO_SYNC_SUPPORTED = 1;

	public static final int NOTIF_MAX_CONTACT_SUPPORTED = 2;

	public static final int NOTIF_CONTACTS_NOT_SYNCED = 3;

	public static final int NOTIF_PHOTO_NOT_SYNCED = 4;

	public static final int NOTIF_MISSING_KEY = 5;

	public static final int NOTIF_PAYMENT_VERIFICATIONRESULT = 6;

	public static final int NOTIF_ICON = R.drawable.notif_icon;

	/** Used for ExtrasBundle in SyncRequest to force loading of restrictions */
	public static final String PARAM_GETRESTRICTIONS = "com.ntsync.android.sync.getrestrictions";
}
