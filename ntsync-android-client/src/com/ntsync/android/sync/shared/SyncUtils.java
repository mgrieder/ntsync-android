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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.PeriodicSync;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntsync.shared.Restrictions;

public final class SyncUtils {

	private static final Semaphore CHANGES_LOCK = new Semaphore(1);

	private static final String SYNC_RESTRICTIONS_PHOTOSUPPORT = "com.ntsync.android.sync.restrict.photosupport";
	private static final String SYNC_RESTRICTIONS_MAXCONTACTS = "com.ntsync.android.sync.restrict.maxcontacts";
	private static final String SYNC_RESTRICTIONS_MAXGROUPS = "com.ntsync.android.sync.restrict.maxgroups";
	private static final String SYNC_RESTRICTIONS_VALIDUNTIL = "com.ntsync.android.sync.restrict.validuntil";
	private static final String SYNC_STATE = "com.ntsync.android.sync.state";

	private static final String LAST_PAYMENT = "com.ntsync.android.sync.lastpayment";

	/** Default Interval in seconds */
	private static final int DEFAULT_SYNCINTERVAL = 1440;

	private final static String TAG = "SyncUtils";

	/** Prevents more than one Payment-Verification at the same time */
	private static final Semaphore VERIFICATION_LOCK = new Semaphore(1);

	private SyncUtils() {

	}

	/**
	 * 
	 * @param authority
	 *            the provider to specify in the sync request
	 * @param extras
	 *            extra parameters to go along with the sync request
	 * @param frequency
	 *            in seconds
	 * @param context
	 */
	public static void addPeriodicSync(String authority, Bundle extras,
			long frequency, Context context) {

		AccountManager am = AccountManager.get(context);
		Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		for (Account ac : accounts) {
			ContentResolver.addPeriodicSync(ac, authority, extras, frequency);
		}
	}

	/**
	 * Check if a Sync is active
	 * 
	 * @param accountName
	 * @return true if a Sync is active with the current Account Name
	 */
	public static boolean isSyncActive(String accountName) {
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		return ContentResolver
				.isSyncActive(account, ContactsContract.AUTHORITY);
	}

	public static void removePeriodicSync(String authority, Bundle extras,
			Context context) {
		AccountManager am = AccountManager.get(context);
		Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		for (Account ac : accounts) {
			ContentResolver.removePeriodicSync(ac, authority, extras);
		}
	}

	/**
	 * Get the last saved Sync Result for an account.
	 * 
	 * @param accountManager
	 * @param account
	 * @return Sync Result or null if there was not saved Result.
	 */
	public static AccountSyncResult getSyncResult(
			AccountManager accountManager, Account account) {
		String syncState = accountManager.getUserData(account, SYNC_STATE);

		AccountSyncResult result = null;
		if (syncState != null) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				result = mapper.readValue(syncState, AccountSyncResult.class);
			} catch (IOException e) {
				LogHelper.logW(TAG,
						"Could not parse AccountSyncResult for account: "
								+ account + " Value:" + syncState, e);
			}
		}
		return result;
	}

	/**
	 * Set Sync-Result for an account.
	 * 
	 * @param accountManager
	 * @param account
	 * @param syncResult
	 */
	public static void setSyncResult(AccountManager accountManager,
			Account account, AccountSyncResult syncResult) {

		String strVal = null;
		if (syncResult != null) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				strVal = mapper.writeValueAsString(syncResult);
			} catch (JsonProcessingException e) {
				LogHelper.logE(TAG,
						"Could not save AccountSyncResult for account: "
								+ account, e);
			}
		}
		accountManager.setUserData(account, SYNC_STATE, strVal);
	}

/**
	 * Stop temporarily all DataChange processing, should be in a finally with
	 * {@link #startProcessDataChanges());
	 * @throws InterruptedException 
	 */
	public static void stopProcessDataChanges() throws InterruptedException {
		CHANGES_LOCK.acquire();
	}

	public static void startProcessDataChanges() {
		CHANGES_LOCK.release();
	}

	public static boolean processDataChanges() {
		return CHANGES_LOCK.availablePermits() > 0;
	}

	/**
	 * 
	 * @return false if Verification is already running
	 * @throws InterruptedException
	 */
	public static boolean startPaymentVerification() {
		return VERIFICATION_LOCK.tryAcquire();
	}

	public static void stopPaymentVerification() {
		if (VERIFICATION_LOCK.availablePermits() == 0) {
			VERIFICATION_LOCK.release();
		}
	}

	public static boolean isPaymentVerificationStarted() {
		return VERIFICATION_LOCK.availablePermits() == 0;
	}

	/**
	 * Get Restrictions
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return null if restrictions were never saved before.
	 */
	public static Restrictions getRestrictions(Account account,
			AccountManager accountManager) {
		boolean photoSupport = false;
		int maxContacts = Integer.MAX_VALUE;
		int maxGroups = Integer.MAX_VALUE;
		Date validUntil = null;

		boolean foundRestr = false;
		String photoSupportStr = accountManager.getUserData(account,
				SYNC_RESTRICTIONS_PHOTOSUPPORT);
		if (!TextUtils.isEmpty(photoSupportStr)) {
			photoSupport = Boolean.parseBoolean(photoSupportStr);
			foundRestr = true;
		}
		String maxContactStr = accountManager.getUserData(account,
				SYNC_RESTRICTIONS_MAXCONTACTS);
		if (!TextUtils.isEmpty(maxContactStr)) {
			maxContacts = Integer.parseInt(maxContactStr);
			foundRestr = true;
		}
		String maxGroupStr = accountManager.getUserData(account,
				SYNC_RESTRICTIONS_MAXGROUPS);
		if (!TextUtils.isEmpty(maxGroupStr)) {
			maxGroups = Integer.parseInt(maxGroupStr);
			foundRestr = true;
		}
		String validUntilStr = accountManager.getUserData(account,
				SYNC_RESTRICTIONS_VALIDUNTIL);
		if (!TextUtils.isEmpty(validUntilStr)) {
			validUntil = new Date(Long.parseLong(validUntilStr));
		}

		Restrictions restr = null;
		if (foundRestr) {
			restr = new Restrictions(maxContacts, maxGroups, photoSupport,
					validUntil);
		}

		return restr;
	}

	/**
	 * Save Restrictions
	 * 
	 * @param account
	 * @param accountManager
	 * @param restr
	 *            could be null, then Restriction-Info will be deleted
	 */
	public static void saveRestrictions(Account account,
			AccountManager accountManager, Restrictions restr) {
		String photoSupportStr = restr != null ? Boolean.toString(restr
				.isPhotoSyncSupported()) : null;
		accountManager.setUserData(account, SYNC_RESTRICTIONS_PHOTOSUPPORT,
				photoSupportStr);

		String maxContactsStr = restr != null ? String.valueOf(restr
				.getMaxContactCount()) : null;
		accountManager.setUserData(account, SYNC_RESTRICTIONS_MAXCONTACTS,
				maxContactsStr);

		String maxGroupStr = restr != null ? String.valueOf(restr
				.getMaxGroupCount()) : null;
		accountManager.setUserData(account, SYNC_RESTRICTIONS_MAXGROUPS,
				maxGroupStr);

		Date validDate = restr != null ? restr.getValidUntil() : null;
		accountManager.setUserData(account, SYNC_RESTRICTIONS_VALIDUNTIL,
				validDate != null ? String.valueOf(validDate.getTime()) : null);
	}

	/**
	 * Stores a Payment to make sure that the Payment will be verified and
	 * processed.
	 * 
	 * @param account
	 * @param accountManager
	 * @param paymentConf
	 *            null: clear any stored information.
	 * @param priceId
	 *            null: clear any stored information
	 */
	public static void savePayment(Account account,
			AccountManager accountManager, JSONObject paymentConf, UUID priceId) {
		String storeValue = priceId == null || paymentConf == null ? null
				: priceId.toString() + ";" + paymentConf.toString() + ";"
						+ System.currentTimeMillis();
		accountManager.setUserData(account, LAST_PAYMENT, storeValue);
	}

	/**
	 * Get a stored PaymentConfirmation (has to be verified on the server).
	 * 
	 * @param account
	 * @param accountManager
	 * @return null if none is available.
	 */
	public static PaymentData getPayment(Account account,
			AccountManager accountManager) {
		PaymentData payment = null;
		String paymentData = accountManager.getUserData(account, LAST_PAYMENT);
		if (paymentData != null) {
			int pos1 = paymentData.indexOf(';');
			int pos2 = paymentData.lastIndexOf(';');
			int startTimePos = pos2 + 1;
			if (pos1 > 0 && pos2 > 0 && pos2 > pos1
					&& startTimePos < paymentData.length()) {
				try {
					long paymentSaveDate = Long.parseLong(paymentData
							.substring(startTimePos));
					UUID priceId = UUID.fromString(paymentData.substring(0,
							pos1));
					JSONObject obj = new JSONObject(paymentData.substring(
							pos1 + 1, pos2));
					payment = new PaymentData(priceId, paymentSaveDate, obj);
				} catch (JSONException ex) {
					Log.w(TAG,
							"Invalid PaymentConfirmation data. Data Ignored",
							ex);
				} catch (IllegalArgumentException ex) {
					Log.w(TAG,
							"Invalid PaymentConfirmation data. Data Ignored",
							ex);
				}
			}
			if (payment == null) {
				// Remove invalid PaymentData
				accountManager.setUserData(account, LAST_PAYMENT, null);
			}
		}

		return payment;
	}

	/**
	 * Checks if some PaymentData is not yet verified
	 * 
	 * @param context
	 * @return true if some PaymentData is open to verify
	 */
	public static boolean hasPaymentData(Context context) {
		boolean foundPaymentData = false;
		AccountManager acm = AccountManager.get(context);
		Account[] accounts = acm.getAccountsByType(Constants.ACCOUNT_TYPE);
		for (Account account : accounts) {
			PaymentData paymentData = SyncUtils.getPayment(account, acm);
			if (paymentData != null) {
				foundPaymentData = true;
				break;
			}
		}
		return foundPaymentData;
	}

	/**
	 * Check if a network is active and connected.
	 * 
	 * @param context
	 * @return true if an active network is available.
	 */
	public static boolean isNetworkConnected(Context context) {
		ConnectivityManager cMgr = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cMgr.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnected();
	}

	/**
	 * Create a new Account and activate the automatic sync
	 * 
	 * @param account
	 *            null is not allowed
	 * @param accountManager
	 *            null is not allowed
	 * @param password
	 *            null is not allowed
	 */
	public static boolean createAccount(Context context, final Account account,
			AccountManager accountManager, String password) {
		boolean added = accountManager.addAccountExplicitly(account, password,
				null);
		if (added) {
			List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(
					account, ContactsContract.AUTHORITY);
			if (syncs != null) {
				// Remove default syncs.
				for (PeriodicSync periodicSync : syncs) {
					ContentResolver.removePeriodicSync(account,
							ContactsContract.AUTHORITY, periodicSync.extras);
				}
			}
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(context);
			int synctime;
			try {
				synctime = settings.getInt("pref_synctime",
						DEFAULT_SYNCINTERVAL);
			} catch (ClassCastException e) {
				LogHelper.logI(TAG, "Invalid SyncTime-Settingvalue", e);
				synctime = DEFAULT_SYNCINTERVAL;
			}
			if (synctime != 0) {
				addPeriodicSync(ContactsContract.AUTHORITY, Bundle.EMPTY,
						synctime, context);
			}

			// Set contacts sync for this account.
			ContentResolver.setSyncAutomatically(account,
					ContactsContract.AUTHORITY, true);
		} else {
			LogHelper.logI(TAG, "Account " + account.name
					+ " is already available.");
		}
		return added;
	}

	/**
	 * Struct-Class for transporting temporary saved PaymentConfiguration-Data
	 */
	public static class PaymentData {

		public final UUID priceId;
		public final long paymentSaveDate;

		/** JSON-Object of PaymentConfirmation */
		public final JSONObject paymentConfirmation;

		public PaymentData(UUID priceId, long paymentSaveDate,
				JSONObject paymentConfirmation) {
			super();
			this.priceId = priceId;
			this.paymentSaveDate = paymentSaveDate;
			this.paymentConfirmation = paymentConfirmation;
		}
	}
}
