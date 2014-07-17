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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.PeriodicSync;
import android.provider.ContactsContract;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.platform.ContactManager;
import com.ntsync.android.sync.shared.AccountStatistic;
import com.ntsync.android.sync.shared.AccountSyncResult;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.SyncUtils;
import com.ntsync.shared.Restrictions;

/**
 * A custom Loader that loads all of our sync-accounts.
 */
public class AccountStatisticListLoader extends
		AsyncTaskLoader<List<AccountStatistic>> {

	private static final String TAG = "AccountStatisticListLoader";

	List<AccountStatistic> accountList;
	private final AccountManager accountManager;

	public AccountStatisticListLoader(Context context) {
		super(context);
		accountManager = AccountManager.get(context);
	}

	@Override
	public List<AccountStatistic> loadInBackground() {
		Account[] accounts = accountManager
				.getAccountsByType(Constants.ACCOUNT_TYPE);

		List<AccountStatistic> statList = new ArrayList<AccountStatistic>();
		for (Account account : accounts) {

			String username = account.name;
			int contactCount = -1;
			int contactGroupCount = -1;
			Context ctx = getContext();

			AccountSyncResult syncResult = SyncUtils.getSyncResult(
					accountManager, account);
			Date lastSync = syncResult != null ? syncResult.getLastSyncTime()
					: null;
			Date nextSync = null;

			boolean autoSyncEnabled = ContentResolver
					.getMasterSyncAutomatically()
					&& ContentResolver.getSyncAutomatically(account,
							ContactsContract.AUTHORITY);
			if (autoSyncEnabled) {
				List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(
						account, ContactsContract.AUTHORITY);
				long nextSyncRef = lastSync != null ? lastSync.getTime()
						: System.currentTimeMillis();
				for (PeriodicSync sync : syncs) {
					long nextTime = nextSyncRef + sync.period * 1000;
					if (nextSync == null || nextTime < nextSync.getTime()) {
						nextSync = new Date(nextTime);
					}
				}
			}

			boolean autoSync = ContentResolver.getSyncAutomatically(account,
					ContactsContract.AUTHORITY);

			// Get Restrictions
			Restrictions restr = SyncUtils.getRestrictions(account,
					accountManager);
			if (restr == null) {
				try {
					String authtoken = NetworkUtilities.blockingGetAuthToken(
							accountManager, account, null);
					restr = NetworkUtilities.getRestrictions(getContext(),
							account, authtoken, accountManager);
				} catch (OperationCanceledException e) {
					Log.i(TAG, "Restriction loading canceled from user", e);
				} catch (AuthenticatorException e) {
					Log.w(TAG, "Authenticator failed", e);
				} catch (AuthenticationException e) {
					Log.i(TAG, "Authentification failed", e);
				} catch (NetworkErrorException e) {
					Log.i(TAG, "Loading Restrictions failed", e);
				} catch (ServerException e) {
					Log.i(TAG, "Loading Restrictions failed", e);
				}
			}

			contactCount = ContactManager.getContactCount(ctx, account);
			contactGroupCount = ContactManager.getContactGroupCount(ctx,
					account);

			statList.add(new AccountStatistic(username, contactCount,
					contactGroupCount, restr, syncResult, nextSync, autoSync));

		}
		return statList;
	}

	/**
	 * Called when there is new data to deliver to the client. The super class
	 * will take care of delivering it; the implementation here just adds a
	 * little more logic.
	 */
	@Override
	public void deliverResult(List<AccountStatistic> newData) {
		List<AccountStatistic> oldList = accountList;
		accountList = newData;

		if (isStarted() && (oldList == null || !newData.equals(oldList))) {
			super.deliverResult(newData);
		}
	}

	@Override
	protected void onStartLoading() {
		if (accountList != null) {
			deliverResult(accountList);
		}

		forceLoad();
	}

	@Override
	protected void onStopLoading() {
		cancelLoad();
	}

	@Override
	protected void onReset() {
		super.onReset();

		onStopLoading();
		accountList = null;
	}
}