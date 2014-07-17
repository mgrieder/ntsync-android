package com.ntsync.android.sync.syncadapter;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on SyncAdapter.java from the SampleSyncAdapter-Example in Android SDK
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

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.KeyPasswordActivity;
import com.ntsync.android.sync.activities.PaymentVerificationService;
import com.ntsync.android.sync.activities.ShopActivity;
import com.ntsync.android.sync.activities.ViewAccountsActivity;
import com.ntsync.android.sync.client.ClientKeyHelper;
import com.ntsync.android.sync.client.ClientKeyHelper.PrivateKeyState;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.platform.ContactManager;
import com.ntsync.android.sync.platform.RestrictionConflictHandler;
import com.ntsync.android.sync.shared.AccountSyncResult;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.SyncResultState;
import com.ntsync.android.sync.shared.SyncUtils;
import com.ntsync.shared.ContactConstants;
import com.ntsync.shared.ContactGroup;
import com.ntsync.shared.HeaderCreateException;
import com.ntsync.shared.HeaderParseException;
import com.ntsync.shared.RawContact;
import com.ntsync.shared.RequestGenerator.SyncResponse;
import com.ntsync.shared.Restrictions;
import com.ntsync.shared.SyncAnchor;

/**
 * implementation for syncing sample SyncAdapter contacts to the platform
 * ContactOperations provider. This sample shows a basic 2-way sync between the
 * client and a sample server. It also contains an example of how to update the
 * contacts' status messages, which would be useful for a messaging or social
 * networking client.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

	private static final String CLIENT_MOD_FAILED_MSG = "Update ClientMod failed";
	private static final Long FULLSYNC_MARKER = Long.valueOf(-1);
	private static final String TAG = "SyncAdapter";
	private static final String SYNC_CONTACT_MARKER_KEY = "com.ntsync.android.sync.contact.marker";
	private static final String SYNC_CONTACT_PHOTOSAVE_KEY = "com.ntsync.android.sync.contact.photosave";
	private static final String SYNC_GROUP_MARKER_KEY = "com.ntsync.android.sync.group.marker";

	private static final String NOTIF_SHOWN_PHOTO_SYNCED = "com.ntsync.android.sync.contact.notif.photo";
	private static final String NOTIF_SHOWN_CONTACTS_SYNCED = "com.ntsync.android.sync.contact.notif.contacts";

	/**
	 * Time interval in Seconds for a retry when sync was failing due to an
	 * IO-Error.
	 */
	private static final int SYNC_RETRY_DELAY = 60;

	/** Time to wait until notification is shown again */
	private static final long NOTIF_WAIT_TIME = 1000 * 60 * 60 * 24 * 7;

	private final AccountManager mAccountManager;

	private final Context mContext;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
		mAccountManager = AccountManager.get(context);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncResult) {

		PaymentVerificationService.startVerificationTimer(mContext
				.getApplicationContext());

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		boolean onlyWifi = settings.getBoolean("sync_onlywifi", false);
		if (onlyWifi) {
			WifiManager wifiManager = (WifiManager) mContext
					.getSystemService(Context.WIFI_SERVICE);
			if (!wifiManager.isWifiEnabled()) {
				Log.i(TAG, "Cancel Sync, because Wifi is not enabled.");
				syncResult.stats.numIoExceptions++;
				return;
			}
		}

		AccountSyncResult ourSyncResult = new AccountSyncResult(account.name);
		try {
			SyncUtils.stopProcessDataChanges();
			performSync(account, extras, syncResult, ourSyncResult);

			updateClientMod(account);
		} catch (InterruptedException e1) {
			LogHelper.logWCause(TAG, "Failed to sync.", e1);
			syncResult.databaseError = true;
		} finally {
			SyncUtils.startProcessDataChanges();
			ourSyncResult.setLastSyncTime(new Date());
			SyncUtils.setSyncResult(mAccountManager, account, ourSyncResult);
		}
	}

	private void performSync(Account account, Bundle extras,
			SyncResult syncResult, AccountSyncResult appSyncResult) {
		String authtoken = null;
		try {

			long contactSyncMarker = getContactSyncMarker(account);
			long contactGroupMarker = getContactGroupSyncMarker(account);
			SyncAnchor syncAnchor = new SyncAnchor();

			if (contactSyncMarker == 0) {
				// By default, contacts from a 3rd party provider are hidden
				// in the contacts list.
				ContactManager.setAccountContactsVisibility(mContext, account,
						true);

				// Add Default Group, otherwise group selection is not
				// visible
				// in the default editor.
				ContactManager.ensureSampleGroupExists(mContext, account);
			}

			authtoken = NetworkUtilities.blockingGetAuthToken(mAccountManager,
					account, null);
			if (authtoken == null) {
				syncResult.stats.numIoExceptions++;
				appSyncResult.setState(SyncResultState.AUTH_FAILED);
				return;
			}

			boolean getRestrictions = extras != null ? extras
					.containsKey(Constants.PARAM_GETRESTRICTIONS) : false;
			Restrictions restr = SyncUtils.getRestrictions(account,
					mAccountManager);
			if (restr == null || getRestrictions) {
				Restrictions oldRestr = restr;
				restr = NetworkUtilities.getRestrictions(mContext, account,
						authtoken, mAccountManager);
				processRestrictions(oldRestr, restr, account);
				// Return-Value is not processed because we don't have to
				// restart for a full sync because all relevant data values will
				// be read after here.
			}

			PrivateKeyState readyState = ClientKeyHelper.isReadyForSync(
					mContext, account, mAccountManager, authtoken);
			boolean retryLater = false;
			switch (readyState) {
			case AUTH_FAILED:
				retryLater = true;
				appSyncResult.setState(SyncResultState.AUTH_FAILED);
				break;
			case CHECK_FAILED:
				// Retry later -> delay sync
				retryLater = true;
				appSyncResult.setState(SyncResultState.SERVER_ERROR);
				break;
			case NETWORK_ERROR:
				appSyncResult.setState(SyncResultState.NETWORK_ERROR);
				appSyncResult.setErrorMsg(readyState.getErrorMsg());
				retryLater = true;
				break;
			case MISSING_KEY:
				sendMissingKey(account, authtoken, readyState.getCurrSalt());
				appSyncResult.setState(SyncResultState.MISSING_KEY);
				return;
			default:
				clearMissingKeyNotification(account);
				break;
			}
			if (retryLater) {
				syncResult.delayUntil = SYNC_RETRY_DELAY;
				syncResult.fullSyncRequested = true;
				return;
			}

			SecretKey privKey = ClientKeyHelper.getOrCreatePrivateKey(account,
					mAccountManager);

			authtoken = checkIfSaltSaved(account, authtoken);
			if (authtoken == null) {
				syncResult.stats.numIoExceptions++;
				appSyncResult.setState(SyncResultState.AUTH_FAILED);
				return;
			}

			// Update ClientMod if not already set.
			ContactManager.updateClientModDate(mContext, account);
			// Get local Dirty Groups
			List<ContactGroup> dirtyGroups = ContactManager.getDirtyGroups(
					mContext, account, restr);
			boolean syncContacts = true;
			if (!dirtyGroups.isEmpty()) {
				// sync only Groups if some new groups are available, to
				// make
				// sure, that ids are available
				for (ContactGroup contactGroup : dirtyGroups) {
					if (contactGroup.getSourceId() == null) {
						syncContacts = false;
						break;
					}
				}
			}
			syncAnchor.setAnchor(ContactConstants.TYPE_CONTACTGROUP,
					contactGroupMarker);

			List<RawContact> dirtyContacts = null;
			Map<Long, String> newIdMap = null;
			if (syncContacts) {

				// Get local Dirty contacts
				dirtyContacts = ContactManager.getDirtyContacts(mContext,
						account, restr, new SyncRestConflictHandler(
								account.name));

				newIdMap = ContactManager.getNewIdMap(mContext, account);
				syncAnchor.setAnchor(ContactConstants.TYPE_CONTACT,
						contactSyncMarker);
			}

			// Send the dirty contacts to the server, and retrieve the
			// server-side changes
			String saltStr = ClientKeyHelper.getSalt(account, mAccountManager);
			boolean explizitPhotoSave = getExplicitSavePhoto(account);
			SyncResponse result = NetworkUtilities.syncContacts(account,
					authtoken, syncAnchor, dirtyContacts, dirtyGroups, privKey,
					mAccountManager, mContext, syncResult, saltStr, newIdMap,
					restr, explizitPhotoSave);

			if (result.newGroupIdMap != null && !result.newGroupIdMap.isEmpty()) {
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Calling contactManager's set new GroupIds. Count Updates:"
									+ result.newGroupIdMap.size());
				}

				ContactManager.saveGroupIds(mContext, account.name,
						result.newGroupIdMap);
			}
			if (result.newContactIdMap != null
					&& !result.newContactIdMap.isEmpty()) {
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Calling contactManager's set new ContactIds. Count Updates:"
									+ result.newContactIdMap.size());
				}

				ContactManager.saveContactIds(mContext, account.name,
						result.newContactIdMap);
			}

			Set<Long> updatedGroupIds = null;
			if (result.serverGroups != null && !result.serverGroups.isEmpty()) {
				// Update the local groups database with the changes.
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Calling contactManager's update groups. Count Updates:"
									+ result.serverGroups.size());
				}
				updatedGroupIds = ContactManager.updateGroups(mContext,
						account.name, result.serverGroups);
			}
			Set<Long> updatedContactIds = null;
			if (result.serverContacts != null
					&& !result.serverContacts.isEmpty()) {
				// Update the local contacts database with the changes.
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Calling contactManager's update contacts. Count Updates:"
									+ result.serverContacts.size());
				}
				updatedContactIds = ContactManager.updateContacts(mContext,
						account.name, result.serverContacts, true, restr);
			}

			SyncAnchor newSyncAnchor = result.newServerAnchor;
			if (newSyncAnchor != null) {
				setContactSyncMarker(account,
						newSyncAnchor.getAnchor(ContactConstants.TYPE_CONTACT));
				setContactGroupSyncMarker(account,
						newSyncAnchor
								.getAnchor(ContactConstants.TYPE_CONTACTGROUP));
			}

			if (result.syncstate != null) {
				switch (result.syncstate) {
				case INVALID_KEY:
					// Reset Key-Info && do FullSync
					mAccountManager.invalidateAuthToken(Constants.ACCOUNT_TYPE,
							authtoken);
					ClientKeyHelper.clearPrivateKeyData(account,
							mAccountManager);
					ContactManager.setDirtyFlag(mContext, account);
					setContactSyncMarker(account, FULLSYNC_MARKER);
					setContactGroupSyncMarker(account, FULLSYNC_MARKER);
					syncResult.fullSyncRequested = true;
					appSyncResult.setState(SyncResultState.SUCCESS);
					return;
				case FORCE_FULLSYNC:
					ContactManager.setDirtyFlag(mContext, account);
					setContactSyncMarker(account, FULLSYNC_MARKER);
					setContactGroupSyncMarker(account, FULLSYNC_MARKER);
					syncResult.fullSyncRequested = true;
					appSyncResult.setState(SyncResultState.SUCCESS);
					return;
				default:
					LogHelper.logI(TAG, "Ignoring unknown SyncState:"
							+ result.syncstate);
					break;
				}
			}

			boolean resync = processRestrictions(restr, result.restrictions,
					account);
			if (resync) {
				ContactManager.setDirtyFlag(mContext, account);
				syncResult.fullSyncRequested = true;
				appSyncResult.setState(SyncResultState.SUCCESS);
				return;
			}

			if (!dirtyGroups.isEmpty()) {
				ContactManager.clearGroupSyncFlags(mContext, dirtyGroups,
						result.newGroupIdMap, updatedGroupIds);
			}

			if (dirtyContacts != null && !dirtyContacts.isEmpty()) {
				ContactManager
						.clearSyncFlags(mContext, dirtyContacts, account.name,
								result.newContactIdMap, updatedContactIds);
			}

			if (newIdMap != null && !newIdMap.isEmpty()) {
				ContactManager.clearServerId(mContext, newIdMap);
			}

			if (!syncContacts) {
				// if only groups were synced, restart for syncing contacts.
				syncResult.fullSyncRequested = true;
			}

			if (explizitPhotoSave) {
				// Clear Explizit Flag
				setExplicitSavePhoto(account, false);
			}
			
			appSyncResult.setState(SyncResultState.SUCCESS);
		} catch (final AuthenticatorException e) {
			LogHelper.logE(TAG, "AuthenticatorException", e);
			syncResult.stats.numParseExceptions++;
			setPrgErrorMsg(appSyncResult, e);
		} catch (final OperationCanceledException e) {
			LogHelper.logI(TAG, "OperationCanceledExcetpion", e);
			appSyncResult.setState(SyncResultState.AUTH_FAILED);
		} catch (final IOException e) {
			LogHelper.logE(TAG, "IOException", e);
			syncResult.stats.numIoExceptions++;
			setPrgErrorMsg(appSyncResult, e);
		} catch (final AuthenticationException e) {
			LogHelper.logI(TAG, "AuthenticationException", e);
			syncResult.stats.numAuthExceptions++;
			appSyncResult.setState(SyncResultState.AUTH_FAILED);
		} catch (final ParseException e) {
			LogHelper.logE(TAG, "ParseException", e);
			syncResult.stats.numParseExceptions++;
			setPrgErrorMsg(appSyncResult, e);
		} catch (InvalidKeyException e) {
			mAccountManager.invalidateAuthToken(Constants.ACCOUNT_TYPE,
					authtoken);
			ClientKeyHelper.clearPrivateKeyData(account, mAccountManager);
			LogHelper.logW(TAG, "InvalidKeyException", e);
			syncResult.stats.numAuthExceptions++;
		} catch (NetworkErrorException e) {
			syncResult.stats.numIoExceptions++;
			LogHelper.logWCause(TAG,
					"Sync failed because of a NetworkException", e);
			appSyncResult.setState(SyncResultState.NETWORK_ERROR);
			appSyncResult.setErrorMsg(String.format(
					getText(R.string.sync_network_error),
					e.getLocalizedMessage()));
		} catch (ServerException e) {
			syncResult.stats.numIoExceptions++;
			LogHelper.logWCause(TAG,
					"Sync failed because of a ServerException", e);
			appSyncResult.setState(SyncResultState.SERVER_ERROR);
			appSyncResult.setErrorMsg(getText(R.string.sync_server_error));
		} catch (OperationApplicationException e) {
			LogHelper.logW(TAG, "Sync failed because a DB-Operation failed", e);
			syncResult.databaseError = true;
			setPrgErrorMsg(appSyncResult, e);
		} catch (HeaderParseException e) {
			syncResult.stats.numIoExceptions++;
			LogHelper
					.logWCause(
							TAG,
							"Sync failed because of server reponse could not be parsed",
							e);
			setPrgErrorMsg(appSyncResult, e);
		} catch (HeaderCreateException e) {
			syncResult.databaseError = true;
			LogHelper.logE(TAG,
					"Sync failed because header could not be created", e);
			setPrgErrorMsg(appSyncResult, e);
		}
	}

	private void setPrgErrorMsg(AccountSyncResult appSyncResult,
			final Throwable e) {
		appSyncResult.setErrorMsg(String
				.format(mContext.getText(R.string.errormsg_programmingerror)
						.toString(), e.getLocalizedMessage()));
	}

	/**
	 * Update Clients Modifications but don't throws exceptions so that a sync
	 * is not disturbed from a failure.
	 * 
	 * @param account
	 */
	private void updateClientMod(Account account) {
		// Update ClientMod for concurrent changes
		try {
			ContactManager.updateClientModDate(mContext, account);
		} catch (IOException e) {
			LogHelper.logW(TAG, CLIENT_MOD_FAILED_MSG, e);
		} catch (OperationApplicationException e) {
			LogHelper.logE(TAG, CLIENT_MOD_FAILED_MSG, e);
		}
	}

	private boolean processRestrictions(Restrictions currRestr,
			Restrictions newRestr, Account account)
			throws OperationApplicationException {
		boolean resync = false;
		if (newRestr != null
				&& (currRestr == null || !currRestr.equals(newRestr))) {
			// Notify User.
			NotificationManager notificationManager = (NotificationManager) mContext
					.getSystemService(Context.NOTIFICATION_SERVICE);
			if (currRestr != null && newRestr.isPhotoSyncSupported()
					&& !currRestr.isPhotoSyncSupported()) {

				// Create ViewAccount-Intent
				Intent viewAccountsIntent = new Intent(mContext,
						ViewAccountsActivity.class);
				// Adds the back stack
				TaskStackBuilder stackBuilder = TaskStackBuilder
						.create(mContext);
				stackBuilder.addParentStack(ViewAccountsActivity.class);
				stackBuilder.addNextIntent(viewAccountsIntent);

				// Photo sync possible.
				NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
						mContext)
						.setSmallIcon(Constants.NOTIF_ICON)
						.setContentTitle(
								mContext.getText(
										R.string.notif_photosyncenabled_title)
										.toString())
						.setContentText(account.name)
						.setAutoCancel(true)
						.setOnlyAlertOnce(true)
						.setContentIntent(
								stackBuilder.getPendingIntent(0,
										PendingIntent.FLAG_UPDATE_CURRENT));
				notificationManager.notify(
						Constants.NOTIF_PHOTO_SYNC_SUPPORTED, mBuilder.build());
				// Resync to sync Photos
				resync = true;

				setExplicitSavePhoto(account, true);
			}
			if (newRestr.isPhotoSyncSupported()) {
				notificationManager.cancel(Constants.NOTIF_PHOTO_NOT_SYNCED);
			}
			if (!newRestr.isPhotoSyncSupported()) {
				notificationManager
						.cancel(Constants.NOTIF_PHOTO_SYNC_SUPPORTED);
			}

			if (currRestr != null
					&& newRestr.getMaxContactCount() != currRestr
							.getMaxContactCount()) {
				// Create ViewAccount-Intent
				Intent viewAccountsIntent = new Intent(mContext,
						ViewAccountsActivity.class);
				// Adds the back stack
				TaskStackBuilder stackBuilder = TaskStackBuilder
						.create(mContext);
				stackBuilder.addParentStack(ViewAccountsActivity.class);
				stackBuilder.addNextIntent(viewAccountsIntent);
				boolean moreAllowed = newRestr.getMaxContactCount() > currRestr
						.getMaxContactCount();
				NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
						mContext)
						.setSmallIcon(Constants.NOTIF_ICON)
						.setContentTitle(
								moreAllowed ? getText(R.string.notif_contact_moresupported_title)
										: getText(R.string.notif_contact_lesssupported_title))
						.setContentText(
								String.format(
										getText(R.string.notif_contact_supported_content),
										newRestr.getMaxContactCount(),
										account.name))
						.setAutoCancel(true)
						.setOnlyAlertOnce(true)
						.setContentIntent(
								stackBuilder.getPendingIntent(0,
										PendingIntent.FLAG_UPDATE_CURRENT));
				notificationManager
						.notify(Constants.NOTIF_MAX_CONTACT_SUPPORTED,
								mBuilder.build());
				// Hide Contacts not syned, but allow the message to show up
				// again if there are still more than allowed.
				notificationManager.cancel(Constants.NOTIF_CONTACTS_NOT_SYNCED);
				unhideMaxContactMessage(mContext, account);
			}

			if (resync) {
				ContactManager.setDirtyFlag(mContext, account);
			}

			SyncUtils.saveRestrictions(account, mAccountManager, newRestr);
		}
		return resync;
	}

	private String getText(int resId) {
		return mContext.getText(resId).toString();
	}

	private String checkIfSaltSaved(Account account, String authtoken)
			throws IOException, OperationCanceledException,
			AuthenticationException, NetworkErrorException, ServerException {
		String newAuthToken = authtoken;
		if (!ClientKeyHelper.isSaltSaved(account, mAccountManager)) {
			String saltStr = ClientKeyHelper.getSalt(account, mAccountManager);
			String pwdCheck = ClientKeyHelper.getPwdCheck(account,
					mAccountManager);
			if (saltStr != null && pwdCheck != null) {
				int retrycount = 1;
				boolean retry;
				do {
					retry = false;
					try {
						boolean saltSaved = NetworkUtilities.savePwdSalt(
								mContext, account.name, newAuthToken, saltStr,
								null, false, pwdCheck);
						if (!saltSaved) {
							mAccountManager.invalidateAuthToken(
									Constants.ACCOUNT_TYPE, newAuthToken);
							ClientKeyHelper.clearPrivateKeyData(account,
									mAccountManager);

							return null;
						}
					} catch (AuthenticationException ex) {
						LogHelper
								.logD(TAG,
										"Authentification failed, trying to get new Token",
										ex);
						// Retry
						mAccountManager.invalidateAuthToken(
								Constants.ACCOUNT_TYPE, newAuthToken);
						if (retrycount > 0) {
							newAuthToken = NetworkUtilities
									.blockingGetAuthToken(mAccountManager,
											account, null);
							if (newAuthToken != null) {
								retry = true;
							}
							retrycount--;
						} else {
							throw ex;
						}
					}
				} while (retry);
				ClientKeyHelper.setSaltSaved(account, mAccountManager);
			} else {
				LogHelper.logW(TAG, "Salt or PwdCheck is null." + saltStr + " "
						+ pwdCheck);
				// Only process if salt and pwd is saved
				return null;
			}
		}
		return newAuthToken;
	}

	/**
	 * last syncAnchor for contact or 0 if we've never synced.
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return last syncAnchor
	 */
	private long getContactSyncMarker(Account account) {
		String markerString = mAccountManager.getUserData(account,
				SYNC_CONTACT_MARKER_KEY);
		if (!TextUtils.isEmpty(markerString)) {
			return Long.parseLong(markerString);
		}
		return 0;
	}

	private void setExplicitSavePhoto(Account account, boolean enabled) {
		mAccountManager.setUserData(account, SYNC_CONTACT_PHOTOSAVE_KEY,
				enabled ? Boolean.TRUE.toString() : null);
	}

	private boolean getExplicitSavePhoto(Account account) {
		String val = mAccountManager.getUserData(account,
				SYNC_CONTACT_PHOTOSAVE_KEY);
		return Boolean.parseBoolean(val);
	}

	/**
	 * Save off the high-water-mark we receive back from the server.
	 * 
	 * @param account
	 *            The account we're syncing
	 * @param marker
	 *            last syncAnchor
	 */
	private void setContactSyncMarker(Account account, Long marker) {
		if (marker != null) {
			mAccountManager.setUserData(account, SYNC_CONTACT_MARKER_KEY,
					Long.toString(marker));
		}
	}

	/**
	 * This helper function fetches the last known high-water-mark we received
	 * from the server - or 0 if we've never synced.
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return last syncAnchor or 0 n.a.
	 */
	private long getContactGroupSyncMarker(Account account) {
		String markerString = mAccountManager.getUserData(account,
				SYNC_GROUP_MARKER_KEY);
		if (!TextUtils.isEmpty(markerString)) {
			return Long.parseLong(markerString);
		}
		return 0;
	}

	/**
	 * Save the last syncAnchor for Group
	 * 
	 * @param account
	 *            The account we're syncing
	 * @param marker
	 *            The high-water-mark we want to save.
	 */
	private void setContactGroupSyncMarker(Account account, Long marker) {
		if (marker != null) {
			mAccountManager.setUserData(account, SYNC_GROUP_MARKER_KEY,
					Long.toString(marker));
		}
	}

	private void clearMissingKeyNotification(Account account) {
		NotificationManager mNotificationManager = (NotificationManager) mContext
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(account.name, Constants.NOTIF_MISSING_KEY);
	}

	private void sendMissingKey(Account account, final String authToken,
			byte[] saltPwdCheck) {
		Intent intent = KeyPasswordActivity.createKeyPasswortActivity(mContext,
				account, authToken, saltPwdCheck);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		PendingIntent resultPendingIntent = PendingIntent.getActivity(mContext,
				0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				mContext)
				.setSmallIcon(R.drawable.notif_key)
				.setContentTitle(
						String.format(mContext.getText(
								R.string.notif_missing_key).toString()))
				.setContentText(account.name).setAutoCancel(false)
				.setOnlyAlertOnce(true).setContentIntent(resultPendingIntent);

		NotificationManager mNotificationManager = (NotificationManager) mContext
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(account.name, Constants.NOTIF_MISSING_KEY,
				mBuilder.build());
	}

	private void notifyUserPhotoNotSynced(String accountName) {
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		AccountManager acm = AccountManager.get(mContext);
		String synced = acm.getUserData(account, NOTIF_SHOWN_PHOTO_SYNCED);
		if (synced == null) {
			Intent shopIntent = new Intent(mContext, ShopActivity.class);
			shopIntent.putExtra(ShopActivity.PARM_ACCOUNT_NAME, accountName);
			TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
			stackBuilder.addParentStack(ShopActivity.class);
			stackBuilder.addNextIntent(shopIntent);

			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
					mContext)
					.setSmallIcon(Constants.NOTIF_ICON)
					.setContentTitle(
							getText(R.string.notif_photonotsynced_title))
					.setContentText(accountName)
					.setAutoCancel(true)
					.setContentIntent(
							stackBuilder.getPendingIntent(0,
									PendingIntent.FLAG_UPDATE_CURRENT))
					.setOnlyAlertOnce(true);
			NotificationManager mNotificationManager = (NotificationManager) mContext
					.getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.notify(Constants.NOTIF_PHOTO_NOT_SYNCED,
					mBuilder.build());
			acm.setUserData(account, NOTIF_SHOWN_PHOTO_SYNCED,
					String.valueOf(System.currentTimeMillis()));
		}
	}

	/**
	 * Allows to show the Contact not synced message again
	 * 
	 * @param context
	 * @param account
	 */
	private static void unhideMaxContactMessage(Context context, Account account) {
		AccountManager acm = AccountManager.get(context);
		acm.setUserData(account, NOTIF_SHOWN_CONTACTS_SYNCED, null);
	}

	private void notifyUserConctactNotSynced(int maxCount,
			int totalLocalContacts, String accountName) {
		AccountManager acm = AccountManager.get(mContext);
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		String lastTimeShown = acm.getUserData(account,
				NOTIF_SHOWN_CONTACTS_SYNCED);
		Long lastTime;
		try {
			lastTime = lastTimeShown != null ? Long.parseLong(lastTimeShown)
					: null;
		} catch (NumberFormatException ex) {
			LogHelper.logWCause(TAG, "Invalid Config-Settings:"
					+ NOTIF_SHOWN_CONTACTS_SYNCED + " Value:" + lastTimeShown,
					ex);
			lastTime = null;
		}

		if (lastTime == null
				|| System.currentTimeMillis() > lastTime.longValue()
						+ NOTIF_WAIT_TIME) {
			// Create Shop-Intent
			Intent shopIntent = new Intent(mContext, ShopActivity.class);
			shopIntent.putExtra(ShopActivity.PARM_ACCOUNT_NAME, account.name);
			// Adds the back stack
			TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
			stackBuilder.addParentStack(ShopActivity.class);
			stackBuilder.addNextIntent(shopIntent);

			// Create Notification
			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
					mContext)
					.setSmallIcon(Constants.NOTIF_ICON)
					.setContentTitle(
							String.format(
									getText(R.string.notif_contactnotsynced_title),
									maxCount, totalLocalContacts))
					.setContentText(
							String.format(
									getText(R.string.notif_contactnotsynced_content),
									account.name))
					.setAutoCancel(true)
					.setOnlyAlertOnce(true)
					.setContentIntent(
							stackBuilder.getPendingIntent(0,
									PendingIntent.FLAG_UPDATE_CURRENT));
			NotificationManager mNotificationManager = (NotificationManager) mContext
					.getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.notify(Constants.NOTIF_CONTACTS_NOT_SYNCED,
					mBuilder.build());
			acm.setUserData(account, NOTIF_SHOWN_CONTACTS_SYNCED,
					String.valueOf(System.currentTimeMillis()));
		}
	}

	private final class SyncRestConflictHandler implements
			RestrictionConflictHandler {

		private final String accountName;

		private SyncRestConflictHandler(String accountName) {
			this.accountName = accountName;
		}

		public void onPhotoNotSynced(long rawContactId, long dataId) {
			notifyUserPhotoNotSynced(accountName);
		}

		public void onContactNotSynced(long rawContactId, int maxCount,
				int totalLocalContacts) {
			notifyUserConctactNotSynced(maxCount, totalLocalContacts,
					accountName);
		}
	}
}
