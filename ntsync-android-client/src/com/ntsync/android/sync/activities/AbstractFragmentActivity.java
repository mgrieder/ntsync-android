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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ntsync.android.sync.BuildConfig;
import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.HoneycombHelper;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.LogHelper;

public abstract class AbstractFragmentActivity extends FragmentActivity {

	private final int removeMenuId;

	private static final String TAG = "DefaultFragmentActivity";

	protected AbstractFragmentActivity() {
		this(-1);
	}

	protected AbstractFragmentActivity(int removeMenuId) {
		this.removeMenuId = removeMenuId;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SystemHelper.initSystem(this);
		// Show the Up button in the action bar.
		setupActionBar(this);
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void setupActionBar(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			HoneycombHelper.activateHomeUp(activity);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.default_menu, menu);
		if (removeMenuId != -1) {
			menu.removeItem(removeMenuId);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean handled = onSelectMenuItem(this, item);
		if (!handled) {
			return super.onOptionsItemSelected(item);
		}
		return false;
	}

	public static boolean onSelectMenuItem(Activity activity, MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(activity);
			return true;
		case R.id.action_settings:
			// Show Settings
			Intent intent = new Intent(activity,
					SettingsPreferenceActivity.class);
			activity.startActivity(intent);
			return true;
		case R.id.action_about:
			// Show Settings
			intent = new Intent(activity, AboutActivity.class);
			activity.startActivity(intent);
			return true;
		case R.id.action_viewkey:
			// Show Key
			intent = new Intent(activity, ViewKeyPasswordActivity.class);
			activity.startActivity(intent);
			return true;
		default:
			if (BuildConfig.DEBUG) {
				throw new IllegalArgumentException("Invalid MenuId:"
						+ item.getItemId());
			}
			LogHelper.logW(TAG, "Unsupported MenuId:" + item.getItemId());
		}
		return false;
	}
}
