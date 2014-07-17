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

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.SystemHelper;

/**
 * About - Screen
 */
public class AboutActivity extends PreferenceActivity {

	/** The tag used to log to adb console. */
	private static final String TAG = "AboutActivity";

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		SystemHelper.initSystem(this);
		addPreferencesFromResource(R.xml.preferences_about);
		AbstractFragmentActivity.setupActionBar(this);
		
		try {
			findPreference("version")
					.setSummary(
							getPackageManager().getPackageInfo(
									getPackageName(), 0).versionName);
		} catch (NameNotFoundException e) {
			Log.w(TAG, "Could not access package info.", e);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.default_menu, menu);
		menu.removeItem(R.id.action_about);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean handled = AbstractFragmentActivity.onSelectMenuItem(this, item);
		if (!handled) {
			return super.onOptionsItemSelected(item);
		}
		return false;
	}
}