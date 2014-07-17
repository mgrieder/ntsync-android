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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.SyncUtils;

/**
 */
public class SettingsPreferenceActivity extends PreferenceActivity {

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		SystemHelper.initSystem(this);
		addPreferencesFromResource(R.xml.preferences_sync);
		Preference timePref = findPreference("sync_time");
		timePref.setOnPreferenceChangeListener(syncTimeChange);
		AbstractFragmentActivity.setupActionBar(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.default_menu, menu);
		menu.removeItem(R.id.action_settings);
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

	Preference.OnPreferenceChangeListener syncTimeChange = new Preference.OnPreferenceChangeListener() {
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			long time = Long.parseLong((String) newValue) * 60;

			SyncUtils.removePeriodicSync(ContactsContract.AUTHORITY,
					Bundle.EMPTY, SettingsPreferenceActivity.this);

			if (time != 0) {
				SyncUtils.addPeriodicSync(ContactsContract.AUTHORITY,
						Bundle.EMPTY, time, SettingsPreferenceActivity.this);
			}

			return true;
		}
	};
}