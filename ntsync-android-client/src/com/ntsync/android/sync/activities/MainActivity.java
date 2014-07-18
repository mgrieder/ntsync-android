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

import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.ntsync.android.sync.BuildConfig;
import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.ContactManager;
import com.ntsync.android.sync.platform.GingerbreadHelper;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;

/**
 * Main-Activity which shows the important tasks and sync-state
 * 
 * <ul>
 * <li>If automatic sync is disabled, the main icon is a sync-button (yellow if
 * there are outstanding sync-data, otherwise green).</li>
 * <li>If automatic sync is enabled the main icon is either green (all data
 * synced) or yellow (outstanding sync-data)</li>
 * </ul>
 */
public class MainActivity extends FragmentActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SystemHelper.initSystem(this);

		if (BuildConfig.DEBUG
				&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			GingerbreadHelper.setStrictMode();
		}

		setContentView(R.layout.activity_main);

		PaymentVerificationService
				.startVerificationTimer(getApplicationContext());

		// Check if account available (but not for resume)
		if (savedInstanceState == null) {
			AccountManager accountManager = AccountManager.get(this);
			Account[] accounts = accountManager
					.getAccountsByType(Constants.ACCOUNT_TYPE);
			if (accounts == null || accounts.length == 0) {
				// Show Login / Register Activity
				Intent intent = new Intent(this, AuthenticatorActivity.class);
				startActivity(intent);
			}
		}
		initViewPeopleBtn();
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateButtonState();
	}

	private void updateButtonState() {
		AccountManager accountManager = AccountManager.get(this);
		Account[] accounts = accountManager
				.getAccountsByType(Constants.ACCOUNT_TYPE);
		boolean accountAvailable = accounts != null && accounts.length > 0;

		this.findViewById(R.id.btnImport).setEnabled(accountAvailable);
		this.findViewById(R.id.btnAccount).setEnabled(accountAvailable);
	}

	public void handleImport(View view) {
		Intent intent = new Intent(this, ImportActivity.class);
		startActivity(intent);
	}

	public void handleViewAccounts(View view) {
		Intent intent = new Intent(this, ViewAccountsActivity.class);
		startActivity(intent);
	}

	private void initViewPeopleBtn() {
		TextView startBtn = (TextView) findViewById(R.id.btnPeople);
		Intent contactAppIntent = ContactManager.createContactAppIntent();
		boolean isIntentSafe = false;
		if (contactAppIntent != null) {
			PackageManager packageManager = getPackageManager();
			List<ResolveInfo> activities = packageManager
					.queryIntentActivities(contactAppIntent, 0);
			isIntentSafe = !activities.isEmpty();

			Drawable icon = ContactManager.getContactAppIcon(this,
					contactAppIntent);
			startBtn.setCompoundDrawablesWithIntrinsicBounds(null, icon, null,
					null);
		}
		startBtn.setEnabled(isIntentSafe);
	}

	public void handleViewPeople(View view) {
		Intent intent = ContactManager.createContactAppIntent();
		if (intent != null) {
			this.startActivity(intent);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
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
