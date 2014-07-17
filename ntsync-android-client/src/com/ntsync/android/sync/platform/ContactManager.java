package com.ntsync.android.sync.platform;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on ContactManager.java from the SampleSyncAdapter-Example in Android SDK
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

import static com.ntsync.android.sync.shared.LogHelper.logWCause;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.MD5Digest;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.ContactConstants.AddressType;
import com.ntsync.shared.ContactConstants.EmailType;
import com.ntsync.shared.ContactConstants.EventType;
import com.ntsync.shared.ContactConstants.ImProtocolType;
import com.ntsync.shared.ContactConstants.ImType;
import com.ntsync.shared.ContactConstants.NicknameType;
import com.ntsync.shared.ContactConstants.OrganizationType;
import com.ntsync.shared.ContactConstants.PhoneType;
import com.ntsync.shared.ContactConstants.RelationType;
import com.ntsync.shared.ContactConstants.WebsiteType;
import com.ntsync.shared.ContactGroup;
import com.ntsync.shared.ListRawData;
import com.ntsync.shared.ListRawData.RawAddressData;
import com.ntsync.shared.ListRawData.RawImData;
import com.ntsync.shared.ListRawData.RawOrganizationData;
import com.ntsync.shared.RawContact;
import com.ntsync.shared.Restrictions;

/**
 * Class for managing contacts sync related mOperations
 */
public final class ContactManager {

	private static final int BATCH_SIZE = 50;

	private ContactManager() {

	}

	private static final String CONTACTSAPP_PACKAGE_NAME = "com.android.contacts";

	private static final String TAG = "ContactManager";

	/**
	 * Take a list of updated contacts and apply those changes to the contacts
	 * database.
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param account
	 *            The username for the account
	 * @param rawContacts
	 *            The list of contacts to update
	 * @param lastSyncMarker
	 *            The previous server sync-state
	 * @return updated RawContactIds (without new/deleted Records)
	 * @throws OperationApplicationException
	 * @throws IOException
	 *             when saving a photo failed.
	 */
	public static synchronized Set<Long> updateContacts(Context context,
			String account, List<RawContact> rawContacts, boolean inSync,
			Restrictions restr) throws OperationApplicationException,
			IOException {

		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);

		Log.i(TAG, "In updateContacts");
		Map<String, Long> cachedGroupIds = new HashMap<String, Long>();
		int insertIndex = 0;

		Set<Long> updatedIds = new HashSet<Long>();
		Set<Long> photoRawIdContacts = new HashSet<Long>();
		LinkedList<Integer> insertList = new LinkedList<Integer>();
		String rawContentUri = RawContacts.CONTENT_URI.toString();
		List<RawContact> toValidateContact = new ArrayList<RawContact>();

		for (final RawContact rawContact : rawContacts) {
			final long rawContactId = rawContact.getRawContactId();
			boolean calcHash = rawContact.getPhoto() != null;
			if (rawContactId > 0) {
				if (!rawContact.isDeleted()) {
					toValidateContact.add(rawContact);
					// Check if Contact is available
					if (isRawContactAvailable(resolver, rawContactId)) {
						Log.d(TAG, "Update Contact " + rawContact.getBestName());
						updateContact(context, resolver, rawContact, inSync,
								rawContactId, batchOperation, cachedGroupIds,
								account, restr);
						if (calcHash) {
							photoRawIdContacts.add(rawContactId);
						}
						updatedIds.add(rawContactId);
					} else {
						Log.d(TAG, "Add Contact " + rawContact.getBestName());
						addContact(context, account, rawContact, inSync,
								batchOperation);
						if (calcHash) {
							insertList.add(insertIndex);
						}
						insertIndex++;
					}
				} else {
					Log.d(TAG, "Delete Contact " + rawContactId);
					deleteContact(context, rawContactId, batchOperation,
							account);
				}
			} else {
				if (!rawContact.isDeleted()) {
					Log.d(TAG, "In addContact");
					toValidateContact.add(rawContact);
					addContact(context, account, rawContact, inSync,
							batchOperation);
					if (calcHash) {
						insertList.add(insertIndex);
					}
					insertIndex++;
				}
			}

			// A sync adapter should batch operations on multiple contacts,
			// because it will make a dramatic performance difference.
			// (UI updates, etc)
			if (batchOperation.size() >= BATCH_SIZE
					|| batchOperation.isBlobSizeBig()) {
				processUpdateOps(context, batchOperation, resolver,
						photoRawIdContacts, insertList, rawContentUri,
						toValidateContact);
				insertIndex = 0;
			}
		}
		processUpdateOps(context, batchOperation, resolver, photoRawIdContacts,
				insertList, rawContentUri, toValidateContact);

		return updatedIds;
	}

	/**
	 * Update client Modification Date for all Accounts.
	 * 
	 * @param context
	 */
	public static void checkForModifications(Context context) {
		AccountManager acm = AccountManager.get(context);
		Account[] accounts = acm.getAccountsByType(Constants.ACCOUNT_TYPE);
		for (Account account : accounts) {
			try {
				updateClientModDate(context, account);
			} catch (IOException e) {
				if (Log.isLoggable(TAG, Log.WARN)) {
					Log.w(TAG, "Update ClientModificationDate failed for "
							+ account.name, e);
				}
			} catch (OperationApplicationException e) {
				if (Log.isLoggable(TAG, Log.WARN)) {
					Log.w(TAG, "Update ClientModificationDate failed for "
							+ account.name, e);
				}
			}
		}
	}

	private static void processUpdateOps(Context context,
			final BatchOperation batchOperation, ContentResolver resolver,
			Set<Long> photoRawIdContacts, LinkedList<Integer> insertList,
			String rawContentUri, List<RawContact> toValidateContact)
			throws OperationApplicationException {
		List<Uri> ids = batchOperation.execute();
		if (!insertList.isEmpty()) {
			int nextInsertIndex = insertList.removeFirst();
			int uriInsertIndex = 0;
			for (Uri uri : ids) {
				if (uri != null) {
					String uriPath = uri.toString();
					if (uriPath != null && uriPath.startsWith(rawContentUri)) {
						if (nextInsertIndex == uriInsertIndex) {
							photoRawIdContacts.add(ContentUris.parseId(uri));
							if (insertList.isEmpty()) {
								break;
							} else {
								nextInsertIndex = insertList.removeFirst();
							}
						}
						uriInsertIndex++;
					}
				}
			}
		}
		insertList.clear();

		// Calculate New Hash-Value of Photos
		for (final Long rawContactId : photoRawIdContacts) {
			setNewHashValue(context, batchOperation, rawContactId);
		}
		photoRawIdContacts.clear();
		// Hash-Update could change version
		batchOperation.execute();

		// Set new Client-Version
		validateClientModVersion(toValidateContact, resolver, batchOperation);
		toValidateContact.clear();
		batchOperation.execute();
	}

	private static void validateClientModVersion(List<RawContact> rawContacts,
			final ContentResolver resolver, final BatchOperation batchOperation)
			throws OperationApplicationException {
		// Validate Version
		for (final RawContact rawContact : rawContacts) {
			if (rawContact.isDeleted()) {
				continue;
			}
			final long rawContactId = rawContact.getRawContactId();

			String selection;
			String[] selArgs;
			if (rawContactId > 0) {
				selection = RawContactQuery.SELECTION;
				selArgs = new String[] { String.valueOf(rawContactId) };
			} else {
				selection = RawContactQuery.SERVER_SELECTION;
				selArgs = new String[] { String.valueOf(rawContact
						.getServerContactId()) };
			}
			// Search bei RawContactId
			Cursor c = resolver.query(RawContactQuery.CONTENT_URI,
					RawContactQuery.PROJECTION_FULL, selection, selArgs, null);
			try {
				while (c.moveToNext()) {
					long currVersion = c
							.getLong(RawContactQuery.COLUMN_VERSION);
					long myVersion = c
							.isNull(RawContactQuery.COLUMN_CLIENT_VERSION) ? -1
							: c.getLong(RawContactQuery.COLUMN_CLIENT_VERSION);
					if (currVersion != myVersion) {
						final ContactOperations contactOp = ContactOperations
								.updateExistingContact(rawContactId, true,
										batchOperation);
						final long id = c.getLong(RawContactQuery.COLUMN_ID);
						final Uri uri = ContentUris.withAppendedId(
								RawContactQuery.CONTENT_URI, id);
						contactOp.updateClientMod(currVersion, null, uri);
					}
				}
			} finally {
				c.close();
			}
		}
		batchOperation.execute();
	}

	/**
	 * Calculate New Hash Value of the current thumbnail.
	 * 
	 * @param context
	 * @param rawContact
	 * @param batchOperation
	 * @param rawContactId
	 */
	private static void setNewHashValue(Context context,
			BatchOperation batchOperation, long rawContactId) {
		// get photo and set new hash and version, because thumbnail will be
		// generated from the system.
		// Read photo
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(DataQuery.CONTENT_URI,
				DataQuery.PROJECTION, DataQuery.SELECTION_TYPE,
				new String[] { String.valueOf(rawContactId),
						Photo.CONTENT_ITEM_TYPE }, null);
		try {
			while (c.moveToNext()) {
				byte[] photo = c.getBlob(DataQuery.COLUMN_PHOTO_IMAGE);
				if (photo != null) {
					// Generate Hash
					Digest digest = new MD5Digest();
					byte[] resBuf = new byte[digest.getDigestSize()];
					digest.update(photo, 0, photo.length);
					digest.doFinal(resBuf, 0);
					String hash = Base64.encodeToString(resBuf, Base64.DEFAULT);
					int currVersion = c.getInt(DataQuery.COLUMN_VERSION);
					int newVersion = currVersion++;

					// Set Hash
					final ContactOperations contactOp = ContactOperations
							.updateExistingContact(rawContactId, true,
									batchOperation);
					final long id = c.getLong(DataQuery.COLUMN_ID);
					final Uri uri = ContentUris.withAppendedId(
							Data.CONTENT_URI, id);
					contactOp.updatePhotoHash(hash, newVersion, uri);
				}
			}
		} finally {
			c.close();
		}
	}

	/**
	 * Create an Intent for showing the Contact/People App
	 * 
	 * @return
	 */
	public static Intent createContactAppIntent() {
		Intent intent = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			intent = ICSMR1Helper.createContactAppIntent();
		} else {
			intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
			// old name is mapped from gingerbread to
			// activities.PeopleActivity
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
					&& Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				intent.setComponent(new ComponentName(CONTACTSAPP_PACKAGE_NAME,
						"com.android.contacts.activities.ContactsFrontDoor"));
			} else {
				intent.setComponent(new ComponentName(CONTACTSAPP_PACKAGE_NAME,
						"com.android.contacts.DialtactsContactsEntryActivity"));
			}
		}
		if (intent != null) {
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		}
		return intent;
	}

	/**
	 * 
	 * @param ctx
	 * @param contactAppIntent
	 * @return null if icon not found
	 */
	public static Drawable getContactAppIcon(Context ctx,
			Intent contactAppIntent) {
		Drawable icon = null;
		try {
			PackageManager packageManager = ctx.getPackageManager();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
				icon = packageManager.getActivityIcon(contactAppIntent);
			} else {
				icon = packageManager
						.getApplicationIcon(CONTACTSAPP_PACKAGE_NAME);
			}
		} catch (NameNotFoundException ex) {
			LogHelper.logW(TAG, "Icon for people app not found", ex);
		}
		return icon;
	}

	/**
	 * Take a list of updated contactgroups and apply those changes to the
	 * contacts database.
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param accountName
	 *            The username for the account
	 * @param rawContacts
	 *            The list of contacts to update
	 * @param lastSyncMarker
	 *            The previous server sync-state
	 * @return updated Groups (without new /deleted Groups)
	 * @throws OperationApplicationException
	 */
	public static synchronized Set<Long> updateGroups(Context context,
			String accountName, List<ContactGroup> contactGroups)
			throws OperationApplicationException {

		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		Set<Long> updatedIds = new HashSet<Long>();

		Log.i(TAG, "In updateGroups");
		for (final ContactGroup group : contactGroups) {
			final String sourceId = group.getSourceId();
			if (TextUtils.isEmpty(sourceId)) {
				Log.w(TAG, "Group has no SourceId");
				continue;
			}

			Long gId = isGroupAvailable(resolver, sourceId, accountName);
			if (!group.isDeleted()) {
				// Check if Contact is available
				if (gId != null) {
					Log.d(TAG, "Update Group " + group.getTitle());
					updateGroup(resolver, group, true, gId, batchOperation);
					updatedIds.add(gId);
				} else {
					Log.d(TAG, "Add Group " + group.getTitle());
					addGroup(accountName, group, true, batchOperation);
				}
			} else if (gId != null) {
				Log.d(TAG, "Delete Group " + gId);
				deleteContactGroup(gId, batchOperation);
			}
			// A sync adapter should batch operations on multiple contacts,
			// because it will make a dramatic performance difference.
			// (UI updates, etc)
			if (batchOperation.size() >= BATCH_SIZE) {
				batchOperation.execute();
			}
		}
		batchOperation.execute();
		return updatedIds;
	}

	/**
	 * @param context
	 * @param account
	 * @return count of groups which are not dirty
	 */
	private static int getNotDirtyGroupCount(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(GroupQuery.CONTENT_URI,
				GroupQuery.PROJECTION, GroupQuery.SELECTION_NOTDIRTY,
				new String[] { account.name }, null);
		int count;
		try {
			count = c.getCount();
		} finally {
			c.close();
		}
		return count;
	}

	private static int getNotDirtyContactCount(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(RawContactAllQuery.CONTENT_URI,
				RawContactAllQuery.PROJECTION,
				RawContactAllQuery.SELECTION_NOTDIRTY,
				new String[] { account.name }, null);
		int count;
		try {
			count = c.getCount();
		} finally {
			c.close();
		}
		return count;
	}

	/**
	 * 
	 * @param context
	 * @param account
	 * @return Count of all contacts
	 */
	public static int getContactCount(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(RawContactAllQuery.CONTENT_URI,
				RawContactAllQuery.PROJECTION,
				RawContactAllQuery.SELECTION_NOTDELETED,
				new String[] { account.name }, null);
		int count;
		try {
			count = c.getCount();
		} finally {
			c.close();
		}
		return count;
	}

	/**
	 * 
	 * @param context
	 * @param account
	 * @return Count of all contacts
	 */
	public static int getContactGroupCount(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(GroupQuery.CONTENT_URI,
				GroupQuery.PROJECTION, GroupQuery.SELECTION_NOTDELETED,
				new String[] { account.name }, null);
		int count;
		try {
			count = c.getCount();
		} finally {
			c.close();
		}
		return count;
	}

	/**
	 * Return a list of the local groups that have been marked as "dirty", and
	 * need syncing to the server.
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param account
	 *            The account that we're interested in syncing
	 * @param restr
	 * @return a list of Groups that are considered "dirty". Never null
	 */
	public static List<ContactGroup> getDirtyGroups(Context context,
			Account account, Restrictions restr) {
		Log.i(TAG, "*** Looking for local dirty groups");
		List<ContactGroup> dirtyGroups = new ArrayList<ContactGroup>();

		final ContentResolver resolver = context.getContentResolver();
		List<ContactGroup> newGroups = new ArrayList<ContactGroup>();
		final Cursor c = resolver.query(DirtyGroupQuery.CONTENT_URI,
				DirtyGroupQuery.PROJECTION, DirtyGroupQuery.SELECTION,
				new String[] { account.name }, null);
		int delGroupCount = 0;
		try {
			while (c.moveToNext()) {
				final long rawId = c.getLong(DirtyGroupQuery.COLUMN_RAW_ID);
				final String serverContactId = c
						.getString(DirtyGroupQuery.COLUMN_SERVER_ID);
				final boolean isDirty = "1".equals(c
						.getString(DirtyGroupQuery.COLUMN_DIRTY));
				final boolean isDeleted = "1".equals(c
						.getString(DirtyGroupQuery.COLUMN_DELETED));

				final long version = c.getLong(DirtyGroupQuery.COLUMN_VERSION);

				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG, "Dirty Contact Nr: " + Long.toString(rawId));
					Log.i(TAG, "Contact Version: " + Long.toString(version));
				}

				if (isDeleted) {
					Log.i(TAG, "Group is marked for deletion");
					ContactGroup group = ContactGroup.createDeletedGroup(rawId,
							serverContactId);
					dirtyGroups.add(group);
					delGroupCount++;
				} else if (isDirty) {
					ContactGroup group = getGroup(context, rawId);
					if (group != null) {
						if (Log.isLoggable(TAG, Log.INFO)) {
							Log.i(TAG, "Group Name: " + group.getTitle());
						}
						if (serverContactId == null) {
							newGroups.add(group);
						} else {
							dirtyGroups.add(group);
						}
					}
				}
			}

		} finally {
			if (c != null) {
				c.close();
			}
		}

		int groupCount = getNotDirtyGroupCount(context, account)
				+ dirtyGroups.size() - delGroupCount;
		int maxGroupCount = restr != null ? restr.getMaxGroupCount()
				: Integer.MAX_VALUE;
		// Add new Groups as long there is room for new groups
		for (ContactGroup contactGroup : newGroups) {
			if (groupCount >= maxGroupCount) {
				break;
			}
			dirtyGroups.add(contactGroup);
			groupCount++;
		}

		return dirtyGroups;
	}

	private static ContactGroup getGroup(Context context, long rawId) {
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(GroupAllQuery.CONTENT_URI,
				GroupAllQuery.PROJECTION, GroupAllQuery.SELECTION,
				new String[] { String.valueOf(rawId) }, null);

		ContactGroup group = null;
		try {
			while (c.moveToNext()) {
				long groupId = c.getLong(GroupAllQuery.COLUMN_ID);
				final String title = c.getString(GroupAllQuery.COLUMN_TITLE);
				final String notes = c.getString(GroupAllQuery.COLUMN_NOTES);
				final String sourceId = c
						.getString(GroupAllQuery.COLUMN_SOURCEID);
				final long version = c.getLong(GroupAllQuery.COLUMN_VERSION);
				group = new ContactGroup(groupId, sourceId, title, notes,
						false, null, version);
			} // while
		} finally {
			c.close();
		}

		return group;
	}

	/**
	 * Return a list of the local contacts that have been marked as "dirty", and
	 * need syncing to the server.
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param account
	 *            The account that we're interested in syncing
	 * @param restr
	 * @return a list of Users that are considered "dirty"
	 * @throws IOException
	 *             when photo files could not be loaded
	 * @throws OperationApplicationException
	 */
	public static List<RawContact> getDirtyContacts(Context context,
			Account account, Restrictions restr,
			RestrictionConflictHandler conflictHandler) throws IOException,
			OperationApplicationException {
		Log.i(TAG, "*** Looking for local dirty contacts");
		List<RawContact> dirtyContacts = new ArrayList<RawContact>();
		List<RawContact> newContacts = new ArrayList<RawContact>();

		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(DirtyQuery.CONTENT_URI,
				DirtyQuery.PROJECTION, DirtyQuery.SELECTION,
				new String[] { account.name }, null);

		int delContactCount = 0;
		try {
			Map<Long, String> cachedGroupIds = new HashMap<Long, String>();
			while (c.moveToNext()) {
				final long rawContactId = c
						.getLong(DirtyQuery.COLUMN_RAW_CONTACT_ID);
				final String serverContactId = c
						.getString(DirtyQuery.COLUMN_SERVER_ID);
				final boolean isDirty = "1".equals(c
						.getString(DirtyQuery.COLUMN_DIRTY));
				final boolean isDeleted = "1".equals(c
						.getString(DirtyQuery.COLUMN_DELETED));

				if (Log.isLoggable(TAG, Log.INFO)) {
					final long version = c.getLong(DirtyQuery.COLUMN_VERSION);
					final long clientMod = c
							.isNull(DirtyQuery.COLUMN_CLIENTMOD) ? -1 : c
							.getLong(DirtyQuery.COLUMN_CLIENTMOD);
					Log.i(TAG,
							"Dirty Contact Nr: "
									+ Long.toString(rawContactId)
									+ " Version:"
									+ Long.toString(version)
									+ " ModDate:"
									+ (clientMod > 0 ? new Date(clientMod)
											.toString() : "null"));
				}

				if (isDeleted) {
					Log.i(TAG, "Contact is marked for deletion");
					RawContact rawContact = RawContact.createDeletedContact(
							rawContactId, serverContactId);
					dirtyContacts.add(rawContact);
					delContactCount++;
				} else if (isDirty) {
					RawContact rawContact = getRawContact(context,
							rawContactId, cachedGroupIds, account.name, null,
							restr, conflictHandler);
					if (rawContact != null) {
						if (Log.isLoggable(TAG, Log.INFO)) {
							Log.i(TAG,
									"Contact Name: " + rawContact.getBestName());
						}
						if (serverContactId == null) {
							newContacts.add(rawContact);
						} else {
							dirtyContacts.add(rawContact);
						}
					}

				}
			}

		} finally {
			if (c != null) {
				c.close();
			}
		}

		final int notNewContactCount = getNotDirtyContactCount(context, account)
				+ dirtyContacts.size() - delContactCount;
		int maxCount = restr != null ? restr.getMaxContactCount()
				: Integer.MAX_VALUE;
		// Add new Groups as long there is room for new contacts
		int contactCount = notNewContactCount;
		for (RawContact contact : newContacts) {
			if (contactCount >= maxCount) {
				// Notification
				int totalLocalContacts = notNewContactCount
						+ newContacts.size();
				if (conflictHandler != null) {
					conflictHandler.onContactNotSynced(
							contact.getRawContactId(), maxCount,
							totalLocalContacts);
				}
				break;
			}
			dirtyContacts.add(contact);
			contactCount++;
		}

		return dirtyContacts;
	}

	public static void updateClientModDate(Context context, Account account)
			throws IOException, OperationApplicationException {
		Log.i(TAG, "*** Looking for old modification date");

		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(DirtyQuery.CONTENT_URI,
				DirtyQuery.PROJECTION, DirtyQuery.SELECTION,
				new String[] { account.name }, null);
		// iterate contact record of them, when version has changed -> set new
		// modification date on contact-row and save new version

		final BatchOperation batchOperation = new BatchOperation(resolver);

		try {
			while (c.moveToNext()) {
				final long rawContactId = c
						.getLong(DirtyQuery.COLUMN_RAW_CONTACT_ID);
				final long lastVersion = c
						.isNull(DirtyQuery.COLUMN_LASTVERSION) ? -1L : c
						.getLong(DirtyQuery.COLUMN_LASTVERSION);
				final long version = c.getLong(DirtyQuery.COLUMN_VERSION);
				if (version > lastVersion) {
					// Update ClientMod
					final ContactOperations contactOp = ContactOperations
							.updateExistingContact(rawContactId, true,
									batchOperation);
					Uri rawContactUri = ContentUris.withAppendedId(
							RawContacts.CONTENT_URI, rawContactId);
					contactOp.updateClientMod(version,
							System.currentTimeMillis(), rawContactUri);
				}

				if (batchOperation.size() >= BATCH_SIZE) {
					batchOperation.execute();
				}
			}
			batchOperation.execute();

		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	/**
	 * Get all new Raw Ids with the corresponding Server Id.
	 * 
	 * @param context
	 * @param account
	 * @return RawId to ServerId Mapping. Could contains Null serverIds, when
	 *         row is marked wrongly as NewRow
	 */
	public static Map<Long, String> getNewIdMap(Context context, Account account) {
		Log.i(TAG, "*** Looking for new ids");
		Map<Long, String> newIdMap = new HashMap<Long, String>();

		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(NewIdQuery.CONTENT_URI,
				NewIdQuery.PROJECTION, NewIdQuery.SELECTION,
				new String[] { account.name }, null);
		try {
			while (c.moveToNext()) {
				final long rawContactId = c
						.getLong(NewIdQuery.COLUMN_RAW_CONTACT_ID);
				final String serverContactId = c
						.getString(NewIdQuery.COLUMN_SERVER_ID);
				LogHelper
						.logD(TAG, "NewId Nr: {}", Long.toString(rawContactId));
				LogHelper.logD(TAG, "ServerId Nr: {}", serverContactId);
				newIdMap.put(rawContactId, serverContactId);
			}

		} finally {
			if (c != null) {
				c.close();
			}
		}
		return newIdMap;
	}

	public static void saveGroupIds(Context context, String name,
			Map<Long, String> newGroupIdMap)
			throws OperationApplicationException {

		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		for (Map.Entry<Long, String> entry : newGroupIdMap.entrySet()) {
			String sourceId = entry.getValue();
			setGroupSourceId(entry.getKey(), sourceId, batchOperation);
		}
		batchOperation.execute();
	}

	public static void saveContactIds(Context context, String accountname,
			Map<Long, String> newContactIdMap)
			throws OperationApplicationException {

		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		for (Map.Entry<Long, String> entry : newContactIdMap.entrySet()) {
			String sourceId = entry.getValue();
			setContactSourceId(entry.getKey(), sourceId, batchOperation);
		}
		batchOperation.execute();
	}

	private static void setContactSourceId(long rawContactId, String sourceId,
			BatchOperation batchOperation) {
		ContactOperations op = ContactOperations.updateExistingContact(
				rawContactId, true, batchOperation);
		final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
				rawContactId);
		op.updateServerId(sourceId, uri);
	}

	/**
	 * Clean up the dirty sync-state and permanently delete all rows.
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param dirtyContacts
	 *            The list of contacts that we're cleaning up
	 * @param newContactIdMap
	 * @param updatedContactIds
	 * @throws OperationApplicationException
	 */
	public static void clearSyncFlags(Context context,
			List<RawContact> dirtyContacts, String accountName,
			Map<Long, String> newContactIdMap, Set<Long> updatedContactIds)
			throws OperationApplicationException {
		Log.i(TAG, "*** Clearing Sync-related Flags");
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		String[] selArgs = new String[] { null, null };

		for (RawContact rawContact : dirtyContacts) {
			if (rawContact.isDeleted()) {
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Deleting contact: "
									+ Long.toString(rawContact
											.getRawContactId()));
				}
				deleteContact(context, rawContact.getRawContactId(),
						batchOperation, accountName);
			} else if (rawContact.isDirty()) {
				Long rawId = rawContact.getRawContactId();
				if (newContactIdMap != null
						&& newContactIdMap.containsKey(rawId)
						&& TextUtils.isEmpty(newContactIdMap.get(rawId))) {
					// For New Contacts which are not saved don't clear Dirty
					// Flags
					continue;
				}
				long contactId = rawContact.getRawContactId();
				long version = rawContact.getVersion();
				// Only reset dirty if not changed in between or changed from
				// server
				boolean clearDirty = true;
				if (updatedContactIds == null
						|| !updatedContactIds.contains(contactId)) {
					selArgs[0] = String.valueOf(contactId);
					selArgs[1] = String.valueOf(version);
					Cursor c = resolver.query(RawContactQuery.CONTENT_URI,
							RawContactQuery.PROJECTION_ID,
							RawContactQuery.VERSION_SELECTION, selArgs, null);
					try {
						clearDirty = c.moveToNext();
					} finally {
						c.close();
					}
				}

				if (clearDirty) {
					if (Log.isLoggable(TAG, Log.INFO)) {
						Log.i(TAG,
								"Clearing dirty flag for: "
										+ rawContact.getBestName());
					}

					clearDirtyFlag(rawContact.getRawContactId(), batchOperation);
				}
			}
		}
		batchOperation.execute();
	}

	/**
	 * Clean up the dirty sync-state and permanently delete all rows for
	 * ContactGroups
	 * 
	 * @param context
	 *            The context of Authenticator Activity
	 * @param newGroupIdMap
	 * @param updatedGroupIds
	 * @param dirtyContacts
	 *            The list of contacts that we're cleaning up
	 * @throws OperationApplicationException
	 */
	public static void clearGroupSyncFlags(Context context,
			List<ContactGroup> dirtyGroups, Map<Long, String> newGroupIdMap,
			Set<Long> updatedGroupIds) throws OperationApplicationException {
		Log.i(TAG, "*** Clearing Sync-related Flags");
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		String[] selArgs = new String[] { null, null };
		for (ContactGroup group : dirtyGroups) {
			if (group.isDeleted()) {
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"Deleting contactgroup: "
									+ Long.toString(group.getRawId()));
				}
				deleteContactGroup(group.getRawId(), batchOperation);
			} else {
				Long rawId = group.getRawId();
				if (newGroupIdMap != null && newGroupIdMap.containsKey(rawId)
						&& TextUtils.isEmpty(newGroupIdMap.get(rawId))) {
					// For Groups which are not saved don't clear Dirty Flags
					continue;
				}

				long version = group.getVersion();

				boolean clearDirty = true;
				if (updatedGroupIds == null || !updatedGroupIds.contains(rawId)) {
					// Only reset dirty if not changed in between or changed
					// from server
					selArgs[0] = String.valueOf(rawId);
					selArgs[1] = String.valueOf(version);
					Cursor c = resolver.query(GroupQuery.CONTENT_URI,
							GroupQuery.PROJECTION,
							GroupQuery.VERSION_SELECTION, selArgs, null);

					try {
						clearDirty = c.moveToNext();
					} finally {
						c.close();
					}
				}

				if (clearDirty) {
					if (Log.isLoggable(TAG, Log.INFO)) {
						Log.i(TAG,
								"Clearing dirty flag for: " + group.getTitle());
					}

					clearGroupDirtyFlag(group.getRawId(), batchOperation);
				}
			}
		}
		batchOperation.execute();
	}

	public static void clearServerId(Context context, Map<Long, String> newIdMap)
			throws OperationApplicationException {
		Log.i(TAG, "*** Clearing ServerId");
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);
		for (Long rawId : newIdMap.keySet()) {
			deleteMyServerId(rawId, batchOperation);
		}
		batchOperation.execute();
	}

	/**
	 * Set the Dirty Flag for all contacts and contactgroups
	 * 
	 * @param context
	 * @param account
	 * @throws OperationApplicationException
	 */
	public static void setDirtyFlag(Context context, Account account)
			throws OperationApplicationException {
		setContactGroupDirtyFlag(context, account);
		setContactDirtyFlag(context, account);
	}

	public static void setContactGroupDirtyFlag(Context context, Account account)
			throws OperationApplicationException {
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);

		final Cursor c = resolver.query(GroupQuery.CONTENT_URI,
				GroupQuery.PROJECTION, GroupQuery.SELECTION_ALL,
				new String[] { account.name }, null);
		try {
			while (c.moveToNext()) {
				final long groupId = c.getLong(GroupQuery.COLUMN_ID);
				final GroupOperations groupOp = GroupOperations
						.updateExistingGroup(true, batchOperation);

				final Uri uri = ContentUris.withAppendedId(Groups.CONTENT_URI,
						groupId);
				groupOp.updateDirtyFlag(true, uri);
			}

		} finally {
			if (c != null) {
				c.close();
			}
		}

		batchOperation.execute();
	}

	/**
	 * Set the Dirty Flag for all contacts
	 * 
	 * @param context
	 * @param account
	 * @throws OperationApplicationException
	 */
	public static void setContactDirtyFlag(Context context, Account account)
			throws OperationApplicationException {
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(resolver);

		final Cursor c = resolver.query(RawContactAllQuery.CONTENT_URI,
				RawContactAllQuery.PROJECTION, RawContactAllQuery.SELECTION,
				new String[] { account.name }, null);
		try {
			while (c.moveToNext()) {
				final long rawContactId = c
						.getLong(RawContactAllQuery.COLUMN_RAW_CONTACT_ID);
				final ContactOperations contactOp = ContactOperations
						.updateExistingContact(rawContactId, true,
								batchOperation);

				final Uri uri = ContentUris.withAppendedId(
						RawContacts.CONTENT_URI, rawContactId);
				contactOp.updateDirtyFlag(true, uri);
			}

		} finally {
			if (c != null) {
				c.close();
			}
		}

		batchOperation.execute();
	}

	/**
	 * Adds a single contact to the platform contacts provider.
	 * 
	 * @param context
	 *            the Authenticator Activity context
	 * @param accountName
	 *            the account the contact belongs to
	 * @param rawContact
	 *            the sample SyncAdapter User object
	 * @param inSync
	 *            is the add part of a client-server sync?
	 * @param batchOperation
	 *            allow us to batch together multiple operations into a single
	 *            provider call
	 * @throws IOException
	 */
	public static void addContact(Context context, String accountName,
			RawContact rawContact, boolean inSync, BatchOperation batchOperation)
			throws IOException {

		// Put the data in the contacts provider
		final ContactOperations contactOp = ContactOperations.createNewContact(
				rawContact.getServerContactId(), accountName,
				rawContact.isStarred(), rawContact.getDroidCustomRingtone(),
				rawContact.isSendToVoiceMail(), rawContact.getLastModified(),
				inSync, batchOperation);

		contactOp.addName(rawContact.getFullName(), rawContact.getFirstName(),
				rawContact.getLastName(), rawContact.getMiddleName(),
				rawContact.getSuffixName(), rawContact.getPrefixName(),
				rawContact.getPhoneticGivenName(),
				rawContact.getPhoneticMiddleName(),
				rawContact.getPhoneticFamilyName());

		List<ListRawData<EmailType>> emails = rawContact.getEmail();
		if (emails != null) {
			for (ListRawData<EmailType> email : emails) {
				contactOp.addEmail(email.getData(),
						getAndroidEmailType(email.getType()), email.getLabel(),
						email.isPrimary(), email.isSuperPrimary());
			}
		}

		List<ListRawData<PhoneType>> phones = rawContact.getPhone();
		if (phones != null) {
			for (ListRawData<PhoneType> phone : phones) {
				contactOp.addPhone(phone.getData(),
						getAndroidPhoneType(phone.getType()), phone.getLabel(),
						phone.isPrimary(), phone.isSuperPrimary());
			}
		}

		List<ListRawData<WebsiteType>> websites = rawContact.getWebsite();
		if (websites != null) {
			for (ListRawData<WebsiteType> website : websites) {
				contactOp.addWebsite(website.getData(),
						getAndroidWebsiteType(website.getType()),
						website.getLabel(), website.isPrimary(),
						website.isSuperPrimary());
			}
		}

		List<RawAddressData> addresses = rawContact.getAddress();
		if (addresses != null) {
			for (RawAddressData address : addresses) {
				contactOp.addAddress(address.getCity(), address.getCountry(),
						address.getLabel(),
						getAndroidAddressType(address.getType()),
						address.getNeighborhood(), address.getPobox(),
						address.getPostcode(), address.getRegion(),
						address.getStreet(), address.isPrimary(),
						address.isSuperPrimary());
			}
		}

		List<ListRawData<EventType>> events = rawContact.getEvents();
		if (events != null) {
			for (ListRawData<EventType> event : events) {
				contactOp.addEvent(event.getData(),
						getAndroidEventType(event.getType()), event.getLabel(),
						event.isPrimary(), event.isSuperPrimary());
			}
		}

		List<RawImData> imAddresses = rawContact.getImAddresses();
		if (imAddresses != null) {
			for (RawImData imAdr : imAddresses) {
				contactOp.addIm(imAdr.getData(),
						getAndroidImType(imAdr.getType()), imAdr.getLabel(),
						imAdr.isPrimary(), imAdr.isSuperPrimary(),
						getAndroidImProtocolType(imAdr.getProtType()),
						imAdr.getCustomProtocolName());
			}
		}

		List<ListRawData<NicknameType>> nicknames = rawContact.getNicknames();
		if (nicknames != null) {
			for (ListRawData<NicknameType> nickname : nicknames) {
				contactOp.addNickname(nickname.getData(),
						getAndroidNicknameType(nickname.getType()),
						nickname.getLabel(), nickname.isPrimary(),
						nickname.isSuperPrimary());
			}
		}

		contactOp.addNote(rawContact.getNote());

		contactOp.addPhoto(rawContact.getPhoto(), context, accountName,
				rawContact.isPhotoSuperPrimary());

		List<String> sourceIds = rawContact.getGroupSourceIds();
		if (sourceIds != null) {
			for (String sourceId : sourceIds) {
				contactOp.addGroupMembership(sourceId);
			}
		}
		List<Long> groupIds = rawContact.getGroupIds();
		if (groupIds != null) {
			for (Long groupId : groupIds) {
				contactOp.addGroupMembership(groupId);
			}
		}

		RawOrganizationData org = rawContact.getOrganization();
		if (org != null) {
			contactOp.addOrganization(org.getData(),
					getAndroidOrganizationType(org.getType()), org.getLabel(),
					org.isPrimary(), org.isSuperPrimary(), org.getTitle(),
					org.getDepartment(), org.getJobDescription());
		}

		List<ListRawData<RelationType>> relations = rawContact.getRelations();
		if (relations != null) {
			for (ListRawData<RelationType> relation : relations) {
				contactOp.addRelation(relation.getData(),
						getAndroidRelationType(relation.getType()),
						relation.getLabel(), relation.isPrimary(),
						relation.isSuperPrimary());
			}
		}
	}

	private static void addGroup(String accountName, ContactGroup group,
			boolean inSync, BatchOperation batchOperation) {

		GroupOperations.createNewGroup(group.getSourceId(), accountName,
				group.getTitle(), group.getNotes(), inSync, batchOperation);
	}

	public static int getAndroidPhoneType(PhoneType phoneType) {
		switch (phoneType) {
		case TYPE_ASSISTANT:
			return Phone.TYPE_ASSISTANT;
		case TYPE_CALLBACK:
			return Phone.TYPE_CALLBACK;
		case TYPE_CAR:
			return Phone.TYPE_CAR;
		case TYPE_COMPANY_MAIN:
			return Phone.TYPE_COMPANY_MAIN;
		case TYPE_CUSTOM:
			return Phone.TYPE_CUSTOM;
		case TYPE_FAX_HOME:
			return Phone.TYPE_FAX_HOME;
		case TYPE_FAX_WORK:
			return Phone.TYPE_FAX_WORK;
		case TYPE_HOME:
			return Phone.TYPE_HOME;
		case TYPE_ISDN:
			return Phone.TYPE_ISDN;
		case TYPE_MAIN:
			return Phone.TYPE_MAIN;
		case TYPE_MMS:
			return Phone.TYPE_MMS;
		case TYPE_MOBILE:
			return Phone.TYPE_MOBILE;
		case TYPE_OTHER:
			return Phone.TYPE_OTHER;
		case TYPE_OTHER_FAX:
			return Phone.TYPE_OTHER_FAX;
		case TYPE_PAGER:
			return Phone.TYPE_PAGER;
		case TYPE_RADIO:
			return Phone.TYPE_RADIO;
		case TYPE_TELEX:
			return Phone.TYPE_TELEX;
		case TYPE_TTY_TDD:
			return Phone.TYPE_TTY_TDD;
		case TYPE_WORK:
			return Phone.TYPE_WORK;
		case TYPE_WORK_MOBILE:
			return Phone.TYPE_WORK_MOBILE;
		case TYPE_WORK_PAGER:
			return Phone.TYPE_WORK_PAGER;
		default:
			return Phone.TYPE_HOME;
		}
	}

	public static PhoneType getPhoneType(int androidPhoneType) {
		switch (androidPhoneType) {
		case Phone.TYPE_ASSISTANT:
			return PhoneType.TYPE_ASSISTANT;
		case Phone.TYPE_CALLBACK:
			return PhoneType.TYPE_CALLBACK;
		case Phone.TYPE_CAR:
			return PhoneType.TYPE_CAR;
		case Phone.TYPE_COMPANY_MAIN:
			return PhoneType.TYPE_COMPANY_MAIN;
		case Phone.TYPE_CUSTOM:
			return PhoneType.TYPE_CUSTOM;
		case Phone.TYPE_FAX_HOME:
			return PhoneType.TYPE_FAX_HOME;
		case Phone.TYPE_FAX_WORK:
			return PhoneType.TYPE_FAX_WORK;
		case Phone.TYPE_HOME:
			return PhoneType.TYPE_HOME;
		case Phone.TYPE_ISDN:
			return PhoneType.TYPE_ISDN;
		case Phone.TYPE_MAIN:
			return PhoneType.TYPE_MAIN;
		case Phone.TYPE_MMS:
			return PhoneType.TYPE_MMS;
		case Phone.TYPE_MOBILE:
			return PhoneType.TYPE_MOBILE;
		case Phone.TYPE_OTHER:
			return PhoneType.TYPE_OTHER;
		case Phone.TYPE_OTHER_FAX:
			return PhoneType.TYPE_OTHER_FAX;
		case Phone.TYPE_PAGER:
			return PhoneType.TYPE_PAGER;
		case Phone.TYPE_RADIO:
			return PhoneType.TYPE_RADIO;
		case Phone.TYPE_TELEX:
			return PhoneType.TYPE_TELEX;
		case Phone.TYPE_TTY_TDD:
			return PhoneType.TYPE_TTY_TDD;
		case Phone.TYPE_WORK:
			return PhoneType.TYPE_WORK;
		case Phone.TYPE_WORK_MOBILE:
			return PhoneType.TYPE_WORK_MOBILE;
		case Phone.TYPE_WORK_PAGER:
			return PhoneType.TYPE_WORK_PAGER;
		default:
			return PhoneType.TYPE_HOME;
		}
	}

	public static int getAndroidEmailType(EmailType emailType) {
		switch (emailType) {
		case TYPE_CUSTOM:
			return Email.TYPE_CUSTOM;
		case TYPE_HOME:
			return Email.TYPE_HOME;
		case TYPE_MOBILE:
			return Email.TYPE_MOBILE;
		case TYPE_OTHER:
			return Email.TYPE_OTHER;
		case TYPE_WORK:
			return Email.TYPE_WORK;
		default:
			return Email.TYPE_OTHER;
		}
	}

	public static EmailType getEmailType(int androidEmailType) {
		switch (androidEmailType) {
		case Email.TYPE_CUSTOM:
			return EmailType.TYPE_CUSTOM;
		case Email.TYPE_OTHER:
			return EmailType.TYPE_OTHER;
		case Email.TYPE_HOME:
			return EmailType.TYPE_HOME;
		case Email.TYPE_WORK:
			return EmailType.TYPE_WORK;
		case Email.TYPE_MOBILE:
			return EmailType.TYPE_MOBILE;
		default:
			return EmailType.TYPE_OTHER;
		}
	}

	public static int getAndroidWebsiteType(WebsiteType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Website.TYPE_CUSTOM;
		case TYPE_HOME:
			return Website.TYPE_HOME;
		case TYPE_HOMEPAGE:
			return Website.TYPE_HOMEPAGE;
		case TYPE_OTHER:
			return Website.TYPE_OTHER;
		case TYPE_WORK:
			return Website.TYPE_WORK;
		case TYPE_BLOG:
			return Website.TYPE_BLOG;
		case TYPE_FTP:
			return Website.TYPE_FTP;
		case TYPE_PROFILE:
			return Website.TYPE_PROFILE;
		default:
			return Website.TYPE_OTHER;
		}

	}

	public static WebsiteType getWebsiteType(int androidType) {
		switch (androidType) {
		case Website.TYPE_CUSTOM:
			return WebsiteType.TYPE_CUSTOM;
		case Website.TYPE_OTHER:
			return WebsiteType.TYPE_OTHER;
		case Website.TYPE_HOME:
			return WebsiteType.TYPE_HOME;
		case Website.TYPE_WORK:
			return WebsiteType.TYPE_WORK;
		case Website.TYPE_BLOG:
			return WebsiteType.TYPE_BLOG;
		case Website.TYPE_FTP:
			return WebsiteType.TYPE_FTP;
		case Website.TYPE_HOMEPAGE:
			return WebsiteType.TYPE_HOMEPAGE;
		case Website.TYPE_PROFILE:
			return WebsiteType.TYPE_PROFILE;
		default:
			return WebsiteType.TYPE_OTHER;
		}
	}

	public static int getAndroidEventType(EventType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Event.TYPE_CUSTOM;
		case TYPE_ANNIVERSARY:
			return Event.TYPE_ANNIVERSARY;
		case TYPE_BIRTHDAY:
			return Event.TYPE_BIRTHDAY;
		case TYPE_OTHER:
			return Event.TYPE_OTHER;
		default:
			return Event.TYPE_OTHER;
		}
	}

	public static EventType getEventType(int androidType) {
		switch (androidType) {
		case Event.TYPE_CUSTOM:
			return EventType.TYPE_CUSTOM;
		case Event.TYPE_OTHER:
			return EventType.TYPE_OTHER;
		case Event.TYPE_ANNIVERSARY:
			return EventType.TYPE_ANNIVERSARY;
		case Event.TYPE_BIRTHDAY:
			return EventType.TYPE_BIRTHDAY;
		default:
			return EventType.TYPE_OTHER;
		}
	}

	public static int getAndroidRelationType(RelationType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Relation.TYPE_CUSTOM;
		case TYPE_ASSISTANT:
			return Relation.TYPE_ASSISTANT;
		case TYPE_BROTHER:
			return Relation.TYPE_BROTHER;
		case TYPE_CHILD:
			return Relation.TYPE_CHILD;
		case TYPE_DOMESTIC_PARTNER:
			return Relation.TYPE_DOMESTIC_PARTNER;
		case TYPE_FATHER:
			return Relation.TYPE_FATHER;
		case TYPE_FRIEND:
			return Relation.TYPE_FRIEND;
		case TYPE_MANAGER:
			return Relation.TYPE_MANAGER;
		case TYPE_MOTHER:
			return Relation.TYPE_MOTHER;
		case TYPE_PARENT:
			return Relation.TYPE_PARENT;
		case TYPE_PARTNER:
			return Relation.TYPE_PARTNER;
		case TYPE_REFERRED_BY:
			return Relation.TYPE_REFERRED_BY;
		case TYPE_RELATIVE:
			return Relation.TYPE_RELATIVE;
		case TYPE_SISTER:
			return Relation.TYPE_SISTER;
		case TYPE_SPOUSE:
			return Relation.TYPE_SPOUSE;
		default:
			return Relation.TYPE_CUSTOM;
		}
	}

	public static RelationType getRelationType(int androidType) {
		switch (androidType) {
		case Relation.TYPE_CUSTOM:
			return RelationType.TYPE_CUSTOM;
		case Relation.TYPE_ASSISTANT:
			return RelationType.TYPE_ASSISTANT;
		case Relation.TYPE_BROTHER:
			return RelationType.TYPE_BROTHER;
		case Relation.TYPE_CHILD:
			return RelationType.TYPE_CHILD;
		case Relation.TYPE_DOMESTIC_PARTNER:
			return RelationType.TYPE_DOMESTIC_PARTNER;
		case Relation.TYPE_FATHER:
			return RelationType.TYPE_FATHER;
		case Relation.TYPE_FRIEND:
			return RelationType.TYPE_FRIEND;
		case Relation.TYPE_MANAGER:
			return RelationType.TYPE_MANAGER;
		case Relation.TYPE_MOTHER:
			return RelationType.TYPE_MOTHER;
		case Relation.TYPE_PARENT:
			return RelationType.TYPE_PARENT;
		case Relation.TYPE_PARTNER:
			return RelationType.TYPE_PARTNER;
		case Relation.TYPE_REFERRED_BY:
			return RelationType.TYPE_REFERRED_BY;
		case Relation.TYPE_RELATIVE:
			return RelationType.TYPE_RELATIVE;
		case Relation.TYPE_SISTER:
			return RelationType.TYPE_SISTER;
		case Relation.TYPE_SPOUSE:
			return RelationType.TYPE_SPOUSE;
		default:
			return RelationType.TYPE_CUSTOM;
		}
	}

	@SuppressWarnings("deprecation")
	public static int getAndroidNicknameType(NicknameType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Nickname.TYPE_CUSTOM;
		case TYPE_DEFAULT:
			return Nickname.TYPE_DEFAULT;
		case TYPE_INITIALS:
			return Nickname.TYPE_INITIALS;
		case TYPE_MAIDEN_NAME:
			return Nickname.TYPE_MAINDEN_NAME;
		case TYPE_OTHER_NAME:
			return Nickname.TYPE_OTHER_NAME;
		case TYPE_SHORT_NAME:
			return Nickname.TYPE_SHORT_NAME;
		default:
			return Nickname.TYPE_DEFAULT;
		}
	}

	@SuppressWarnings("deprecation")
	public static NicknameType getNicknameType(int androidType) {
		switch (androidType) {
		case Nickname.TYPE_CUSTOM:
			return NicknameType.TYPE_CUSTOM;
		case Nickname.TYPE_DEFAULT:
			return NicknameType.TYPE_DEFAULT;
		case Nickname.TYPE_INITIALS:
			return NicknameType.TYPE_INITIALS;
		case Nickname.TYPE_MAINDEN_NAME:
			return NicknameType.TYPE_MAIDEN_NAME;
		case Nickname.TYPE_OTHER_NAME:
			return NicknameType.TYPE_OTHER_NAME;
		case Nickname.TYPE_SHORT_NAME:
			return NicknameType.TYPE_SHORT_NAME;
		default:
			return NicknameType.TYPE_DEFAULT;
		}
	}

	public static int getAndroidAddressType(AddressType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return StructuredPostal.TYPE_CUSTOM;
		case TYPE_HOME:
			return StructuredPostal.TYPE_HOME;
		case TYPE_OTHER:
			return StructuredPostal.TYPE_OTHER;
		case TYPE_WORK:
			return StructuredPostal.TYPE_WORK;
		default:
			return StructuredPostal.TYPE_OTHER;
		}
	}

	public static AddressType getAddressType(int androidType) {
		switch (androidType) {
		case StructuredPostal.TYPE_CUSTOM:
			return AddressType.TYPE_CUSTOM;
		case StructuredPostal.TYPE_HOME:
			return AddressType.TYPE_HOME;
		case StructuredPostal.TYPE_OTHER:
			return AddressType.TYPE_OTHER;
		case StructuredPostal.TYPE_WORK:
			return AddressType.TYPE_WORK;
		default:
			return AddressType.TYPE_OTHER;
		}
	}

	public static int getAndroidImType(ImType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Im.TYPE_CUSTOM;
		case TYPE_HOME:
			return Im.TYPE_HOME;
		case TYPE_OTHER:
			return Im.TYPE_OTHER;
		case TYPE_WORK:
			return Im.TYPE_WORK;
		default:
			return Im.TYPE_OTHER;
		}
	}

	public static ImType getImType(int androidType) {
		switch (androidType) {
		case Im.TYPE_CUSTOM:
			return ImType.TYPE_CUSTOM;
		case Im.TYPE_HOME:
			return ImType.TYPE_HOME;
		case Im.TYPE_OTHER:
			return ImType.TYPE_OTHER;
		case Im.TYPE_WORK:
			return ImType.TYPE_WORK;
		default:
			return ImType.TYPE_OTHER;
		}
	}

	public static int getAndroidImProtocolType(ImProtocolType type) {
		switch (type) {
		case PROTOCOL_AIM:
			return Im.PROTOCOL_AIM;
		case PROTOCOL_CUSTOM:
			return Im.PROTOCOL_CUSTOM;
		case PROTOCOL_GOOGLE_TALK:
			return Im.PROTOCOL_GOOGLE_TALK;
		case PROTOCOL_ICQ:
			return Im.PROTOCOL_ICQ;
		case PROTOCOL_JABBER:
			return Im.PROTOCOL_JABBER;
		case PROTOCOL_MSN:
			return Im.PROTOCOL_MSN;
		case PROTOCOL_NETMEETING:
			return Im.PROTOCOL_NETMEETING;
		case PROTOCOL_QQ:
			return Im.PROTOCOL_QQ;
		case PROTOCOL_SKYPE:
			return Im.PROTOCOL_SKYPE;
		case PROTOCOL_YAHOO:
			return Im.PROTOCOL_YAHOO;
		default:
			return Im.PROTOCOL_CUSTOM;
		}
	}

	public static ImProtocolType getImProtocolType(int androidType) {
		switch (androidType) {
		case Im.PROTOCOL_AIM:
			return ImProtocolType.PROTOCOL_AIM;
		case Im.PROTOCOL_CUSTOM:
			return ImProtocolType.PROTOCOL_CUSTOM;
		case Im.PROTOCOL_GOOGLE_TALK:
			return ImProtocolType.PROTOCOL_GOOGLE_TALK;
		case Im.PROTOCOL_ICQ:
			return ImProtocolType.PROTOCOL_ICQ;
		case Im.PROTOCOL_JABBER:
			return ImProtocolType.PROTOCOL_JABBER;
		case Im.PROTOCOL_MSN:
			return ImProtocolType.PROTOCOL_MSN;
		case Im.PROTOCOL_NETMEETING:
			return ImProtocolType.PROTOCOL_NETMEETING;
		case Im.PROTOCOL_QQ:
			return ImProtocolType.PROTOCOL_QQ;
		case Im.PROTOCOL_SKYPE:
			return ImProtocolType.PROTOCOL_SKYPE;
		case Im.PROTOCOL_YAHOO:
			return ImProtocolType.PROTOCOL_YAHOO;
		default:
			return ImProtocolType.PROTOCOL_CUSTOM;
		}
	}

	public static int getAndroidOrganizationType(OrganizationType type) {
		switch (type) {
		case TYPE_CUSTOM:
			return Organization.TYPE_CUSTOM;
		case TYPE_OTHER:
			return Organization.TYPE_OTHER;
		case TYPE_WORK:
			return Organization.TYPE_WORK;
		default:
			return Organization.TYPE_OTHER;
		}
	}

	public static OrganizationType getOrganizationType(int androidType) {
		switch (androidType) {
		case Organization.TYPE_CUSTOM:
			return OrganizationType.TYPE_CUSTOM;
		case Organization.TYPE_OTHER:
			return OrganizationType.TYPE_OTHER;
		case Organization.TYPE_WORK:
			return OrganizationType.TYPE_WORK;
		default:
			return OrganizationType.TYPE_OTHER;
		}
	}

	/**
	 * Updates a single contact to the platform contacts provider. This method
	 * can be used to update a contact from a sync operation or as a result of a
	 * user editing a contact record.
	 * 
	 * This operation is actually relatively complex. We query the database to
	 * find all the rows of info that already exist for this Contact. For rows
	 * that exist (and thus we're modifying existing fields), we create an
	 * update operation to change that field. But for fields we're adding, we
	 * create "add" operations to create new rows for those fields.
	 * 
	 * @param context
	 *            the Authenticator Activity context
	 * @param resolver
	 *            the ContentResolver to use
	 * @param rawContact
	 *            the sample SyncAdapter contact object
	 * @param inSync
	 *            is the update part of a client-server sync?
	 * @param rawContactId
	 *            the unique Id for this rawContact in contacts provider
	 * @param batchOperation
	 *            allow us to batch together multiple operations into a single
	 *            provider call
	 * @param accountName
	 * @throws IOException
	 * @throws
	 */
	public static void updateContact(Context context, ContentResolver resolver,
			RawContact rawContact, boolean inSync, long rawContactId,
			BatchOperation batchOperation, Map<String, Long> cachedGroupIds,
			String accountName, Restrictions restr) throws IOException {

		boolean existingPhoto = false;
		boolean existingNote = false;
		boolean existingOrg = false;

		final Cursor c = resolver.query(DataQuery.CONTENT_URI,
				DataQuery.PROJECTION, DataQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);
		final ContactOperations contactOp = ContactOperations
				.updateExistingContact(rawContactId, inSync, batchOperation);

		List<ListRawData<PhoneType>> orgPhones = rawContact.getPhone();
		List<ListRawData<PhoneType>> phones = orgPhones != null ? new ArrayList<ListRawData<PhoneType>>(
				orgPhones) : null;
		List<ListRawData<EmailType>> orgEmails = rawContact.getEmail();
		List<ListRawData<EmailType>> emails = orgEmails != null ? new ArrayList<ListRawData<EmailType>>(
				orgEmails) : null;
		List<ListRawData<WebsiteType>> orgWebsites = rawContact.getWebsite();
		List<ListRawData<WebsiteType>> websites = orgWebsites != null ? new ArrayList<ListRawData<WebsiteType>>(
				orgWebsites) : null;
		List<ListRawData<EventType>> orgEvents = rawContact.getEvents();
		List<ListRawData<EventType>> events = orgEvents != null ? new ArrayList<ListRawData<EventType>>(
				orgEvents) : null;
		List<ListRawData<NicknameType>> orgNicknames = rawContact
				.getNicknames();
		List<ListRawData<NicknameType>> nicknames = orgNicknames != null ? new ArrayList<ListRawData<NicknameType>>(
				orgNicknames) : null;
		List<ListRawData<RelationType>> orgRelations = rawContact
				.getRelations();
		List<ListRawData<RelationType>> relations = orgRelations != null ? new ArrayList<ListRawData<RelationType>>(
				orgRelations) : null;
		List<RawAddressData> orgAddresses = rawContact.getAddress();
		List<RawAddressData> addresses = orgAddresses != null ? new ArrayList<RawAddressData>(
				orgAddresses) : null;
		List<RawImData> orgImAddresses = rawContact.getImAddresses();
		List<RawImData> imAddresses = orgImAddresses != null ? new ArrayList<RawImData>(
				orgImAddresses) : null;
		List<String> orgGroupSourceIds = rawContact.getGroupSourceIds();
		List<Long> groupIds = null;
		if (orgGroupSourceIds != null) {
			// Umwandeln SourceGroupId in Group Id
			groupIds = new ArrayList<Long>();
			for (String groupSourceId : orgGroupSourceIds) {
				Long groupId = cachedGroupIds.get(groupSourceId);
				if (groupId == null) {
					groupId = isGroupAvailable(resolver, groupSourceId,
							accountName);
					if (groupId != null) {
						cachedGroupIds.put(groupSourceId, groupId);
					} else {
						Log.e(TAG, "Group not found for GroupSourceId:"
								+ groupSourceId);
					}
				}
				if (groupId != null) {
					groupIds.add(groupId);
				}
			}
		}

		try {

			// Iterate over the existing rows of data, and update each one
			// with the information we received from the server.
			while (c.moveToNext()) {
				final long id = c.getLong(DataQuery.COLUMN_ID);
				final String mimeType = c.getString(DataQuery.COLUMN_MIMETYPE);
				final Uri uri = ContentUris
						.withAppendedId(Data.CONTENT_URI, id);
				boolean deleteEntry = false;

				if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
					contactOp.updateName(uri,
							c.getString(DataQuery.COLUMN_GIVEN_NAME),
							c.getString(DataQuery.COLUMN_FAMILY_NAME),
							c.getString(DataQuery.COLUMN_FULL_NAME),
							c.getString(DataQuery.COLUMN_MIDDLE_NAME),
							c.getString(DataQuery.COLUMN_PREFIX_NAME),
							c.getString(DataQuery.COLUMN_SUFFIX_NAME),
							c.getString(DataQuery.COLUMN_PHONECTIC_GIVEN),
							c.getString(DataQuery.COLUMN_PHONECTIC_MIDDLE),
							c.getString(DataQuery.COLUMN_PHONECTIC_LAST),
							rawContact.getFirstName(),
							rawContact.getLastName(), rawContact.getFullName(),
							rawContact.getMiddleName(),
							rawContact.getPrefixName(),
							rawContact.getSuffixName(),
							rawContact.getPhoneticGivenName(),
							rawContact.getPhoneticMiddleName(),
							rawContact.getPhoneticFamilyName());
				} else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_PHONE_TYPE);
					PhoneType phoneType = getPhoneType(type);
					deleteEntry = true;
					if (phones != null) {
						// Update Entry with the same type
						for (ListRawData<PhoneType> phoneRawData : phones) {
							if (phoneRawData.getType() == phoneType) {
								contactOp.updatePhone(phoneRawData.getData(),
										phoneRawData.getLabel(),
										phoneRawData.isPrimary(),
										phoneRawData.isSuperPrimary(), uri);
								phones.remove(phoneRawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Email.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_EMAIL_TYPE);
					EmailType emailType = getEmailType(type);
					deleteEntry = true;
					if (emails != null) {
						// Update Entry with the same type
						for (ListRawData<EmailType> rawData : emails) {
							if (rawData.getType() == emailType) {
								contactOp.updateEmail(rawData.getData(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								emails.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Website.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_WEBSITE_TYPE);
					WebsiteType websiteType = getWebsiteType(type);
					deleteEntry = true;
					if (websites != null) {
						// Update Entry with the same type
						for (ListRawData<WebsiteType> rawData : websites) {
							if (rawData.getType() == websiteType) {
								contactOp.updateWebsite(rawData.getData(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								websites.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Event.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_EVENT_TYPE);
					EventType eventType = getEventType(type);
					deleteEntry = true;
					if (events != null) {
						// Update Entry with the same type
						for (ListRawData<EventType> rawData : events) {
							if (rawData.getType() == eventType) {
								contactOp.updateEvent(rawData.getData(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								events.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Relation.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_RELATION_TYPE);
					RelationType relationType = getRelationType(type);
					deleteEntry = true;
					if (relations != null) {
						// Update Entry with the same type
						for (ListRawData<RelationType> rawData : relations) {
							if (rawData.getType() == relationType) {
								contactOp.updateRelation(rawData.getData(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								relations.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Im.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_IM_TYPE);
					ImType imType = getImType(type);
					deleteEntry = true;
					if (imAddresses != null) {
						for (RawImData rawData : imAddresses) {
							// Update Entry with the same type
							if (rawData.getType() == imType) {
								contactOp.updateIm(rawData.getData(), rawData
										.getLabel(), rawData.isPrimary(),
										rawData.isSuperPrimary(),
										getAndroidImProtocolType(rawData
												.getProtType()), rawData
												.getCustomProtocolName(), uri);
								imAddresses.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Nickname.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_NICKNAME_TYPE);
					NicknameType nicknameType = getNicknameType(type);
					deleteEntry = true;
					if (nicknames != null) {
						for (ListRawData<NicknameType> rawData : nicknames) {
							if (rawData.getType() == nicknameType) {
								contactOp.updateNickname(rawData.getData(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								nicknames.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(StructuredPostal.CONTENT_ITEM_TYPE)) {
					final int type = c.getInt(DataQuery.COLUMN_POSTAL_TYPE);
					AddressType adrType = getAddressType(type);
					deleteEntry = true;
					if (addresses != null) {
						for (RawAddressData rawData : addresses) {
							if (rawData.getType() == adrType) {
								contactOp.updateAddress(rawData.getCity(),
										rawData.getCountry(),
										rawData.getNeighborhood(),
										rawData.getPobox(),
										rawData.getPostcode(),
										rawData.getRegion(),
										rawData.getStreet(),
										rawData.getLabel(),
										rawData.isPrimary(),
										rawData.isSuperPrimary(), uri);
								addresses.remove(rawData);
								deleteEntry = false;
								break;
							}
						}
					}
				} else if (mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
					existingPhoto = true;
					// Photo support is disabled an existing photo will not be
					// changed.
					if (restr == null || restr.isPhotoSyncSupported()) {
						int syncVersion = c.getInt(DataQuery.COLUMN_VERSION);
						int isSuperPrimary = c
								.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
						String existingFilename = c
								.getString(DataQuery.COLUMN_SYNC3);
						contactOp.updatePhoto(rawContact.getPhoto(), uri,
								context, accountName, existingFilename,
								syncVersion, isSuperPrimary != 0);
					}
				} else if (mimeType.equals(GroupMembership.CONTENT_ITEM_TYPE)) {
					Long groupRowId = c.getLong(DataQuery.COLUMN_GROUP_ROWID);
					deleteEntry = true;
					if (groupIds != null && groupIds.contains(groupRowId)) {
						groupIds.remove(groupRowId);
						// No update needed
						deleteEntry = false;
					}
				} else if (mimeType.equals(Note.CONTENT_ITEM_TYPE)) {
					existingNote = true;
					contactOp.updateNote(rawContact.getNote(), uri);
				} else if (mimeType.equals(Organization.CONTENT_ITEM_TYPE)) {
					existingOrg = true;
					RawOrganizationData org = rawContact.getOrganization();
					contactOp.updateOrganization(org.getData(),
							getAndroidOrganizationType(org.getType()),
							org.getLabel(), org.getTitle(),
							org.getDepartment(), org.getJobDescription(),
							org.isPrimary(), org.isSuperPrimary(), uri);
				}

				if (deleteEntry) {
					contactOp.deleteData(uri);
				}

			} // while
		} finally {
			c.close();
		}

		// Add new entries if not updated above.
		if (phones != null) {
			for (ListRawData<PhoneType> phoneRawData : phones) {
				contactOp.addPhone(phoneRawData.getData(),
						getAndroidPhoneType(phoneRawData.getType()),
						phoneRawData.getLabel(), phoneRawData.isPrimary(),
						phoneRawData.isSuperPrimary());
			}
		}

		if (emails != null) {
			for (ListRawData<EmailType> rawData : emails) {
				contactOp.addEmail(rawData.getData(),
						getAndroidEmailType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}

		if (events != null) {
			for (ListRawData<EventType> rawData : events) {
				contactOp.addEvent(rawData.getData(),
						getAndroidEventType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}

		if (websites != null) {
			for (ListRawData<WebsiteType> rawData : websites) {
				contactOp.addWebsite(rawData.getData(),
						getAndroidWebsiteType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}

		if (nicknames != null) {
			for (ListRawData<NicknameType> rawData : nicknames) {
				contactOp.addNickname(rawData.getData(),
						getAndroidNicknameType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}
		if (relations != null) {
			for (ListRawData<RelationType> rawData : relations) {
				contactOp.addRelation(rawData.getData(),
						getAndroidRelationType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}
		if (imAddresses != null) {
			for (RawImData rawData : imAddresses) {
				contactOp.addIm(rawData.getData(),
						getAndroidImType(rawData.getType()),
						rawData.getLabel(), rawData.isPrimary(),
						rawData.isSuperPrimary(),
						getAndroidImProtocolType(rawData.getProtType()),
						rawData.getCustomProtocolName());
			}
		}
		if (addresses != null) {
			for (RawAddressData rawData : addresses) {
				contactOp.addAddress(rawData.getCity(), rawData.getCountry(),
						rawData.getLabel(),
						getAndroidAddressType(rawData.getType()),
						rawData.getNeighborhood(), rawData.getPobox(),
						rawData.getPostcode(), rawData.getRegion(),
						rawData.getStreet(), rawData.isPrimary(),
						rawData.isSuperPrimary());
			}
		}

		// Add the photo if we didn't update the existing photo
		if (!existingPhoto && (restr == null || restr.isPhotoSyncSupported())) {
			contactOp.addPhoto(rawContact.getPhoto(), context, accountName,
					rawContact.isPhotoSuperPrimary());
		}
		if (!existingNote) {
			contactOp.addNote(rawContact.getNote());
		}
		if (!existingOrg) {
			RawOrganizationData org = rawContact.getOrganization();
			if (org != null) {
				contactOp.addOrganization(org.getData(),
						getAndroidOrganizationType(org.getType()),
						org.getLabel(), org.isPrimary(), org.isSuperPrimary(),
						org.getTitle(), org.getDepartment(),
						org.getJobDescription());
			}
		}
		if (groupIds != null) {
			for (Long groupId : groupIds) {
				contactOp.addGroupMembership(groupId);
			}
		}

		// Update Contact-Data
		final Cursor rC = resolver.query(RawContactQuery.CONTENT_URI,
				RawContactQuery.PROJECTION_FULL, RawContactQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);
		try {
			while (rC.moveToNext()) {
				int starredInt = rC.getInt(RawContactQuery.COLUMN_STARRED);
				boolean existStarred = starredInt != 0;

				int sendToVoiceInt = rC
						.getInt(RawContactQuery.COLUMN_SEND_TO_VOICEMAIL);
				boolean existSendToVoiceMail = sendToVoiceInt != 0;
				String existCustomRingtone = rC
						.getString(RawContactQuery.COLUMN_CUSTOM_RINGTONE);
				Long contactId = rC.isNull(RawContactQuery.COLUMN_CONTACT_ID) ? null
						: rC.getLong(RawContactQuery.COLUMN_CONTACT_ID);
				long currVersion = rC.getLong(RawContactQuery.COLUMN_VERSION);

				contactOp.updateContact(rawContact.isStarred(), existStarred,
						rawContact.getDroidCustomRingtone(),
						existCustomRingtone, rawContact.isSendToVoiceMail(),
						existSendToVoiceMail, rawContactId, contactId,
						rawContact.getLastModified(), currVersion);
			}
		} finally {
			rC.close();
		}

	}

	private static void updateGroup(ContentResolver resolver,
			ContactGroup group, boolean inSync, Long gId,
			BatchOperation batchOperation) {
		if (gId == null) {
			return;
		}

		final Cursor c = resolver.query(GroupAllQuery.CONTENT_URI,
				GroupAllQuery.PROJECTION, GroupAllQuery.SELECTION,
				new String[] { String.valueOf(gId) }, null);
		final GroupOperations contactOp = GroupOperations.updateExistingGroup(
				inSync, batchOperation);

		try {
			while (c.moveToNext()) {
				final long id = c.getLong(GroupAllQuery.COLUMN_ID);
				final Uri uri = ContentUris.withAppendedId(
						GroupAllQuery.CONTENT_URI, id);
				contactOp.updateGroup(uri,
						c.getString(GroupAllQuery.COLUMN_TITLE),
						c.getString(GroupAllQuery.COLUMN_NOTES),
						group.getTitle(), group.getNotes());
			}
		} finally {
			c.close();
		}
	}

	/**
	 * When we first add a sync adapter to the system, the contacts from that
	 * sync adapter will be hidden unless they're merged/grouped with an
	 * existing contact. But typically we want to actually show those contacts,
	 * so we need to mess with the Settings table to get them to show up.
	 * 
	 * @param context
	 *            the Authenticator Activity context
	 * @param account
	 *            the Account who's visibility we're changing
	 * @param visible
	 *            true if we want the contacts visible, false for hidden
	 */
	public static void setAccountContactsVisibility(Context context,
			Account account, boolean visible) {
		ContentValues values = new ContentValues();
		values.put(RawContacts.ACCOUNT_NAME, account.name);
		values.put(RawContacts.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		values.put(Settings.UNGROUPED_VISIBLE, visible ? 1 : 0);

		context.getContentResolver().insert(Settings.CONTENT_URI, values);
	}

	/**
	 * Get distinct Set of Type and Name from all available contacts.
	 * 
	 * @param context
	 * @return AccountType and AccountName. AccuntInfo with Type==null is count
	 *         of ContactIds
	 */
	public static List<AccountInfo> getContactAccounts(Context context) {
		final ContentResolver resolver = context.getContentResolver();

		Set<String> phoneAccountTypes = new HashSet<String>();
		phoneAccountTypes.add("com.htc.android.pcsc");
		phoneAccountTypes.add(null);
		Set<String> simAccountTypes = new HashSet<String>();
		simAccountTypes.add("com.anddroid.contacts.sim");
		simAccountTypes.add("com.android.contacts.sim");
		Set<String> ignoreAccountTypes = new HashSet<String>();
		ignoreAccountTypes.add("DeviceOnly");

		final AccountManager acm = AccountManager.get(context);
		final Account[] existAccounts = acm.getAccounts();
		final AuthenticatorDescription[] descr = acm.getAuthenticatorTypes();
		Map<String, AccountInfo> acInfo = new HashMap<String, AccountInfo>();
		List<AccountInfo> accounts = new ArrayList<AccountInfo>();
		final Cursor cursor = resolver.query(RawContacts.CONTENT_URI,
				new String[] { RawContacts.ACCOUNT_TYPE,
						RawContacts.ACCOUNT_NAME }, null, null, null);
		if (cursor != null) {
			try {
				while (cursor.moveToNext()) {
					String accountType = cursor.isNull(0) ? null : cursor
							.getString(0);
					String accountName = cursor.isNull(1) ? null : cursor
							.getString(1);

					if (ignoreAccountTypes.contains(accountType)) {
						continue;
					}

					String key = accountType + accountName;
					AccountInfo info = acInfo.get(key);
					if (info != null) {
						info.incContactCount();
						continue;
					}

					boolean isLocalAccount = true;
					String displayName = accountType;
					boolean foundName = false;
					for (AuthenticatorDescription des : descr) {
						if (TextUtils.equals(accountType, des.type)) {
							isLocalAccount = false;
							String acLabel = getAccountLabel(context, des);
							if (acLabel != null) {
								displayName = acLabel;
								foundName = true;
								break;
							}
						}
					}
					if (isLocalAccount) {
						// If an account is available -> not local.
						for (Account account : existAccounts) {
							if (TextUtils.equals(account.type, accountType)) {
								isLocalAccount = false;
								break;
							}
						}
					}

					boolean hideName = false;
					if (!foundName) {
						if (phoneAccountTypes.contains(accountType)) {
							displayName = context.getText(
									R.string.import_activity_phone_entry)
									.toString();
							hideName = true;
						} else if (simAccountTypes.contains(accountType)) {
							displayName = context.getText(
									R.string.import_activity_sim_entry)
									.toString();
							hideName = true;
						}
					}

					info = new AccountInfo(displayName, accountType,
							accountName, isLocalAccount);
					info.setHideName(hideName);
					info.incContactCount();
					accounts.add(info);
					acInfo.put(key, info);
				}
			} finally {
				cursor.close();
			}
		}
		return accounts;
	}

	private static String getAccountLabel(Context context,
			AuthenticatorDescription descr) {
		String label = null;
		try {
			Context authContext = context.createPackageContext(
					descr.packageName, 0);
			label = authContext.getResources().getText(descr.labelId)
					.toString();

		} catch (NameNotFoundException e) {
			logWCause(TAG, "No label name for account type " + descr.type, e);
		} catch (NotFoundException e) {
			logWCause(TAG, "No label resource for account type " + descr.type,
					e);
		} catch (SecurityException e) {
			logWCause(TAG, "No label resource for account type " + descr.type,
					e);
		}
		return label;
	}

	/**
	 * Create a default Group.
	 * 
	 * @param context
	 * @param account
	 * @return
	 */
	public static long ensureSampleGroupExists(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();

		String sampleGroupName = context.getText(R.string.samplegroup_name)
				.toString();
		long groupId = 0;
		final Cursor cursor = resolver.query(Groups.CONTENT_URI,
				new String[] { Groups._ID },
				Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE
						+ "=? AND " + Groups.TITLE + "=?", new String[] {
						account.name, account.type, sampleGroupName }, null);
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					groupId = cursor.getLong(0);
				}
			} finally {
				cursor.close();
			}
		}

		if (groupId == 0) {
			// Sample group doesn't exist yet, so create it
			final ContentValues contentValues = new ContentValues();
			contentValues.put(Groups.ACCOUNT_NAME, account.name);
			contentValues.put(Groups.ACCOUNT_TYPE, account.type);
			contentValues.put(Groups.TITLE, sampleGroupName);

			final Uri newGroupUri = resolver.insert(Groups.CONTENT_URI,
					contentValues);
			groupId = ContentUris.parseId(newGroupUri);
		}
		return groupId;
	}

	private static boolean isRawContactAvailable(ContentResolver resolver,
			long rawContactId) {
		final Cursor c = resolver.query(RawContactQuery.CONTENT_URI,
				RawContactQuery.PROJECTION_ID, RawContactQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);

		boolean available;
		try {
			available = c.moveToNext();
		} finally {
			c.close();
		}

		return available;
	}

	private static Long isGroupAvailable(ContentResolver resolver,
			String sourceId, String accountName) {
		final Cursor c = resolver.query(GroupQuery.CONTENT_URI,
				GroupQuery.PROJECTION, GroupQuery.SELECTION, new String[] {
						String.valueOf(sourceId), accountName }, null);

		Long groupId = null;

		try {
			if (c.moveToNext()) {
				groupId = c.getLong(GroupQuery.COLUMN_ID);
			}
		} finally {
			c.close();
		}

		return groupId;
	}

	/**
	 * Return a User object with data extracted from a contact stored in the
	 * local contacts database.
	 * 
	 * Because a contact is actually stored over several rows in the database,
	 * our query will return those multiple rows of information. We then iterate
	 * over the rows and build the User structure from what we find.
	 * 
	 * @param context
	 *            the Authenticator Activity context
	 * @param rawContactId
	 *            the unique ID for the local contact
	 * @param cached
	 *            Mapping from Group Id to Group Source Id. null is not allowed.
	 * @param accountName
	 *            AccountName of the RawContact. For Import this is only set
	 *            when the Type is one of ours Contact-Type
	 * @param importAccountNameDest
	 *            AccountName of the destination which is used to import groups.
	 * @param restr
	 * @return a User object containing info on that contact
	 * @throws IOException
	 *             when photo file could not be loaded.
	 * @throws OperationApplicationException
	 */
	public static RawContact getRawContact(Context context, long rawContactId,
			Map<Long, String> cachedGroupIds, String accountName,
			String importAccountNameDest, Restrictions restr,
			RestrictionConflictHandler conflictHandler) throws IOException,
			OperationApplicationException {
		String firstName = null;
		String lastName = null;
		String fullName = null;
		String middleName = null;
		String prefixName = null;
		String suffixName = null;
		String phoneFirstName = null;
		String phoneMiddleName = null;
		String phoneLastName = null;
		String serverId = null;

		Date lastModified = null;
		byte[] photo = null;
		boolean photoIsSuperPrimary = false;

		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(DataQuery.CONTENT_URI,
				DataQuery.PROJECTION, DataQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);
		List<ListRawData<PhoneType>> phones = null;
		List<ListRawData<EmailType>> emails = null;
		List<ListRawData<WebsiteType>> websites = null;
		List<ListRawData<EventType>> events = null;
		List<ListRawData<RelationType>> relations = null;
		List<ListRawData<NicknameType>> nicknames = null;
		List<RawAddressData> addresses = null;
		List<RawImData> imAddresses = null;
		String note = null;
		List<String> groupSourceIds = null;
		List<Long> groupIds = null;
		RawOrganizationData org = null;
		boolean starred = false;
		String customRingtone = null;
		boolean sendToVoiceMail = false;

		try {
			while (c.moveToNext()) {
				final String mimeType = c.getString(DataQuery.COLUMN_MIMETYPE);
				if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
					lastName = c.getString(DataQuery.COLUMN_FAMILY_NAME);
					firstName = c.getString(DataQuery.COLUMN_GIVEN_NAME);
					fullName = c.getString(DataQuery.COLUMN_FULL_NAME);
					middleName = c.getString(DataQuery.COLUMN_MIDDLE_NAME);
					suffixName = c.getString(DataQuery.COLUMN_SUFFIX_NAME);
					prefixName = c.getString(DataQuery.COLUMN_PREFIX_NAME);
					phoneFirstName = c
							.getString(DataQuery.COLUMN_PHONECTIC_GIVEN);
					phoneMiddleName = c
							.getString(DataQuery.COLUMN_PHONECTIC_MIDDLE);
					phoneLastName = c
							.getString(DataQuery.COLUMN_PHONECTIC_LAST);
				} else if (mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
					if (restr == null || restr.isPhotoSyncSupported()) {
						int isSuperPrimary = c
								.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
						photo = readPhoto(context, rawContactId,
								importAccountNameDest != null ? null
										: accountName, c);
						photoIsSuperPrimary = isSuperPrimary != 0;
					} else {
						// Notif user one time, that pictures are not
						// synchronized
						if (!c.isNull(DataQuery.COLUMN_PHOTO_IMAGE)
								&& conflictHandler != null) {
							conflictHandler.onPhotoNotSynced(rawContactId,
									c.getLong(DataQuery.COLUMN_ID));
						}
					}
				} else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
					if (phones == null) {
						phones = new ArrayList<ListRawData<PhoneType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					PhoneType phoneType = getPhoneType(c
							.getInt(DataQuery.COLUMN_PHONE_TYPE));
					phones.add(new ListRawData<PhoneType>(c
							.getString(DataQuery.COLUMN_PHONE_NUMBER),
							phoneType, c
									.getString(DataQuery.COLUMN_PHONE_LABEL),
							isPrimary != 0, isSuperPrimary != 0));

				} else if (mimeType.equals(Email.CONTENT_ITEM_TYPE)) {
					if (emails == null) {
						emails = new ArrayList<ListRawData<EmailType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					EmailType type = getEmailType(c
							.getInt(DataQuery.COLUMN_EMAIL_TYPE));
					emails.add(new ListRawData<EmailType>(c
							.getString(DataQuery.COLUMN_EMAIL_ADDRESS), type, c
							.getString(DataQuery.COLUMN_EMAIL_LABEL),
							isPrimary != 0, isSuperPrimary != 0));

				} else if (mimeType.equals(Website.CONTENT_ITEM_TYPE)) {
					if (websites == null) {
						websites = new ArrayList<ListRawData<WebsiteType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					WebsiteType type = getWebsiteType(c
							.getInt(DataQuery.COLUMN_WEBSITE_TYPE));
					websites.add(new ListRawData<WebsiteType>(c
							.getString(DataQuery.COLUMN_WEBSITE_ADDRESS), type,
							c.getString(DataQuery.COLUMN_WEBSITE_LABEL),
							isPrimary != 0, isSuperPrimary != 0));
				} else if (mimeType.equals(Event.CONTENT_ITEM_TYPE)) {
					if (events == null) {
						events = new ArrayList<ListRawData<EventType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					EventType type = getEventType(c
							.getInt(DataQuery.COLUMN_EVENT_TYPE));
					events.add(new ListRawData<EventType>(c
							.getString(DataQuery.COLUMN_EVENT_DATA), type, c
							.getString(DataQuery.COLUMN_EVENT_LABEL),
							isPrimary != 0, isSuperPrimary != 0));
				} else if (mimeType.equals(Relation.CONTENT_ITEM_TYPE)) {
					if (relations == null) {
						relations = new ArrayList<ListRawData<RelationType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					RelationType type = getRelationType(c
							.getInt(DataQuery.COLUMN_RELATION_TYPE));
					relations.add(new ListRawData<RelationType>(c
							.getString(DataQuery.COLUMN_RELATION_DATA), type, c
							.getString(DataQuery.COLUMN_RELATION_LABEL),
							isPrimary != 0, isSuperPrimary != 0));
				} else if (mimeType.equals(Nickname.CONTENT_ITEM_TYPE)) {
					if (nicknames == null) {
						nicknames = new ArrayList<ListRawData<NicknameType>>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					NicknameType type = getNicknameType(c
							.getInt(DataQuery.COLUMN_NICKNAME_TYPE));
					nicknames.add(new ListRawData<NicknameType>(c
							.getString(DataQuery.COLUMN_NICKNAME_DATA), type, c
							.getString(DataQuery.COLUMN_NICKNAME_LABEL),
							isPrimary != 0, isSuperPrimary != 0));
				} else if (mimeType.equals(StructuredPostal.CONTENT_ITEM_TYPE)) {
					if (addresses == null) {
						addresses = new ArrayList<RawAddressData>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					AddressType type = getAddressType(c
							.getInt(DataQuery.COLUMN_POSTAL_TYPE));
					addresses
							.add(new RawAddressData(
									type,
									c.getString(DataQuery.COLUMN_POSTAL_LABEL),
									isPrimary != 0,
									isSuperPrimary != 0,
									c.getString(DataQuery.COLUMN_POSTAL_STREET),
									c.getString(DataQuery.COLUMN_POSTAL_POBOX),
									c.getString(DataQuery.COLUMN_POSTAL_NEIGHBORHOOD),
									c.getString(DataQuery.COLUMN_POSTAL_CITY),
									c.getString(DataQuery.COLUMN_POSTAL_REGION),
									c.getString(DataQuery.COLUMN_POSTAL_POSTCODE),
									c.getString(DataQuery.COLUMN_POSTAL_COUNTRY)));
				} else if (mimeType.equals(Im.CONTENT_ITEM_TYPE)) {
					if (imAddresses == null) {
						imAddresses = new ArrayList<RawImData>();
					}
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					ImType type = getImType(c.getInt(DataQuery.COLUMN_IM_TYPE));
					ImProtocolType proType = getImProtocolType(c
							.getInt(DataQuery.COLUMN_IM_PROTOCOL_TYPE));
					imAddresses.add(new RawImData(c
							.getString(DataQuery.COLUMN_IM_ADDRESS), type, c
							.getString(DataQuery.COLUMN_IM_LABEL),
							isPrimary != 0, isSuperPrimary != 0, proType,
							c.getString(DataQuery.COLUMN_IM_PROTOCOL_NAME)));
				} else if (mimeType.equals(Note.CONTENT_ITEM_TYPE)) {
					note = c.getString(DataQuery.COLUMN_NOTE);
				} else if (mimeType.equals(Organization.CONTENT_ITEM_TYPE)) {
					int isSuperPrimary = c
							.getInt(DataQuery.COLUMN_IS_SUPER_PRIMARY);
					int isPrimary = c.getInt(DataQuery.COLUMN_IS_PRIMARY);
					OrganizationType type = getOrganizationType(c
							.getInt(DataQuery.COLUMN_ORGANIZATION_TYPE));
					org = new RawOrganizationData(
							c.getString(DataQuery.COLUMN_ORGANIZATION_NAME),
							type,
							c.getString(DataQuery.COLUMN_ORGANIZATION_LABEL),
							isPrimary != 0,
							isSuperPrimary != 0,
							c.getString(DataQuery.COLUMN_ORGANIZATION_TITLE),
							c.getString(DataQuery.COLUMN_ORGANIZATION_DEPARTMENT),
							c.getString(DataQuery.COLUMN_ORGANIZATION_JOBTITLE));
				} else if (mimeType.equals(GroupMembership.CONTENT_ITEM_TYPE)
						&& !c.isNull(DataQuery.COLUMN_GROUP_ROWID)) {

					Long groupId = c.getLong(DataQuery.COLUMN_GROUP_ROWID);
					// SourceId
					if (importAccountNameDest != null) {
						// Search existing Group otherwise create Group
						// with the same name
						Long newGroupId = null;
						String groupLongId = cachedGroupIds.get(groupId);
						if (groupLongId != null) {
							newGroupId = Long.parseLong(groupLongId);
						} else {
							newGroupId = getGroupByName(resolver, groupId,
									importAccountNameDest);
							cachedGroupIds.put(groupId,
									String.valueOf(newGroupId));
						}
						if (newGroupId != null) {
							if (groupIds == null) {
								groupIds = new ArrayList<Long>();
							}
							groupIds.add(newGroupId);
						}

					} else {
						String groupSourceId = cachedGroupIds.get(groupId);
						if (groupSourceId == null) {
							groupSourceId = getGroupSourceId(resolver, groupId);
							if (groupSourceId != null) {
								cachedGroupIds.put(groupId, groupSourceId);
							}
						}
						if (groupSourceId != null) {
							if (groupSourceIds == null) {
								groupSourceIds = new ArrayList<String>();
							}
							groupSourceIds.add(groupSourceId);
						}
					}
				}
			} // while
		} finally {
			c.close();
		}
		long version = -1;

		final Cursor rC = resolver.query(RawContactQuery.CONTENT_URI,
				RawContactQuery.PROJECTION_FULL, RawContactQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);
		try {
			while (rC.moveToNext()) {
				int starredInt = rC.getInt(RawContactQuery.COLUMN_STARRED);
				starred = starredInt != 0;

				int sendToVoiceInt = rC
						.getInt(RawContactQuery.COLUMN_SEND_TO_VOICEMAIL);
				sendToVoiceMail = sendToVoiceInt != 0;
				customRingtone = rC
						.getString(RawContactQuery.COLUMN_CUSTOM_RINGTONE);
				if (importAccountNameDest == null || accountName != null) {
					lastModified = rC.isNull(RawContactQuery.COLUMN_LASTMOD) ? null
							: new Date(
									rC.getLong(RawContactQuery.COLUMN_LASTMOD));
				}
				version = rC.getLong(RawContactQuery.COLUMN_VERSION);
			}
		} finally {
			rC.close();
		}

		// Now that we've extracted all the information we care about,
		// create the actual User object.
		RawContact rawContact;
		if (importAccountNameDest != null) {
			rawContact = RawContact.create(fullName, firstName, lastName,
					middleName, prefixName, suffixName, phoneFirstName,
					phoneMiddleName, phoneLastName, phones, emails, websites,
					addresses, events, relations, null, nicknames, imAddresses,
					note, org, photo, photoIsSuperPrimary, null, groupIds,
					starred, customRingtone, sendToVoiceMail, lastModified,
					false, -1, serverId, version);
		} else {
			rawContact = RawContact.create(fullName, firstName, lastName,
					middleName, prefixName, suffixName, phoneFirstName,
					phoneMiddleName, phoneLastName, phones, emails, websites,
					addresses, events, relations, null, nicknames, imAddresses,
					note, org, photo, photoIsSuperPrimary, groupSourceIds,
					null, starred, customRingtone, sendToVoiceMail,
					lastModified, false, rawContactId, serverId, version);
		}

		return rawContact;
	}

	/**
	 * Search a Group by Name (for import) or create a new group.
	 * 
	 * @param resolver
	 * @param groupId
	 * @param importAccountNameDest
	 * @return Group-Id
	 */
	private static Long getGroupByName(ContentResolver resolver, long groupId,
			String importAccountNameDest) {
		// Search Group by Name
		// Get Name
		String groupName = null;
		Cursor cursor = resolver.query(Groups.CONTENT_URI,
				new String[] { Groups.TITLE, }, Groups._ID + "=?",
				new String[] { String.valueOf(groupId) }, null);
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					groupName = cursor.getString(0);
				}
			} finally {
				cursor.close();
			}
		}

		Long newGroupId = null;
		// Search by name with importAccountNameDest
		if (!TextUtils.isEmpty(groupName)) {
			cursor = resolver.query(Groups.CONTENT_URI,
					new String[] { Groups._ID, }, Groups.ACCOUNT_TYPE
							+ "=? AND " + Groups.ACCOUNT_NAME + "=? AND "
							+ Groups.TITLE + "=?", new String[] {
							Constants.ACCOUNT_TYPE, importAccountNameDest,
							groupName }, null);
			try {
				if (cursor.moveToFirst()) {
					newGroupId = cursor.getLong(0);
				}
			} finally {
				cursor.close();
			}

			if (newGroupId == null) {
				// Create a new Group
				ContentValues values = new ContentValues();
				values.put(Groups.TITLE, groupName);
				values.put(Groups.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
				values.put(Groups.ACCOUNT_NAME, importAccountNameDest);
				Uri groupUri = resolver.insert(Groups.CONTENT_URI, values);
				newGroupId = ContentUris.parseId(groupUri);
			}
		}

		return newGroupId;
	}

	private static byte[] readPhoto(Context context, long rawContactId,
			String accountName, final Cursor c) throws IOException,
			OperationApplicationException {
		byte[] photo = c.getBlob(DataQuery.COLUMN_PHOTO_IMAGE);

		if (photo != null) {
			boolean photoChanged = true;
			if (accountName != null) {
				int version = c.getInt(DataQuery.COLUMN_VERSION);
				// Check if better quality is available if photo was not
				// changed

				int saveVersion = c.isNull(DataQuery.COLUMN_SYNC2) ? version - 1
						: c.getInt(DataQuery.COLUMN_SYNC2);
				if (version > saveVersion) {
					String hash = c.getString(DataQuery.COLUMN_SYNC1);
					// Compare Hash
					if (hash != null) {
						Digest digest = new MD5Digest();
						byte[] resBuf = new byte[digest.getDigestSize()];
						digest.update(photo, 0, photo.length);
						digest.doFinal(resBuf, 0);
						String currHash = Base64.encodeToString(resBuf,
								Base64.DEFAULT);
						if (hash.equals(currHash)) {
							photoChanged = false;
						} else {
							Log.i(TAG, "Photo-hash changed, use new photo");
						}
					}
				} else {
					photoChanged = false;
				}
			} else {
				// For Import from another Account-Type: always try to get
				// original-photo.
				photoChanged = false;
			}
			boolean found = false;
			if (photoChanged) {
				// Delete original photo if available
				String photoFilename = c.getString(DataQuery.COLUMN_SYNC3);
				if (!TextUtils.isEmpty(photoFilename)) {
					File pFile = PhotoHelper.getPhotoFile(context,
							photoFilename, accountName);
					if (pFile.exists()) {
						boolean deleted = pFile.delete();
						if (!deleted) {
							LogHelper.logW(
									TAG,
									"PhotFile could not be deleted:"
											+ pFile.getAbsolutePath());
						}
					}
				}
				if (!TextUtils.isEmpty(photoFilename)
						|| !c.isNull(DataQuery.COLUMN_SYNC1)
						|| !c.isNull(DataQuery.COLUMN_SYNC2)) {
					// Delete metadata
					final Uri uri = ContentUris.withAppendedId(
							Data.CONTENT_URI, c.getLong(DataQuery.COLUMN_ID));
					final ContentResolver resolver = context
							.getContentResolver();
					final BatchOperation batchOperation = new BatchOperation(
							resolver);
					final ContactOperations contactOp = ContactOperations
							.updateExistingContact(rawContactId, true,
									batchOperation);
					contactOp.clearPhotoMetadata(uri);
					batchOperation.execute();
				}
			} else if (accountName != null) {
				// Load original photo or display-photo when available
				String photoFilename = c.getString(DataQuery.COLUMN_SYNC3);
				if (!TextUtils.isEmpty(photoFilename)) {
					File pFile = PhotoHelper.getPhotoFile(context,
							photoFilename, accountName);
					if (pFile.exists()) {
						FileInputStream fos = null;
						try {
							fos = new FileInputStream(pFile);
							byte[] buffer = new byte[(int) pFile.length()];
							fos.read(buffer);
							photo = buffer;
							found = true;
						} finally {
							if (fos != null) {
								fos.close();
							}
						}

					}
				}
			}
			// try to load Display-Photo for ICS if not found
			if (!found
					&& (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					&& !c.isNull(DataQuery.COLUMN_PHOTO_FILE_ID)) {
				long photoFileId = c.getLong(DataQuery.COLUMN_PHOTO_FILE_ID);
				byte[] displayPhoto = ICSPhotoHelper.getDisplayPhoto(context,
						photoFileId);
				if (displayPhoto != null) {
					photo = displayPhoto;
				}
			}
		}
		return photo;
	}

	private static String getGroupSourceId(ContentResolver resolver,
			long groupId) {
		String sourceGroupId = null;
		final Cursor cursor = resolver.query(Groups.CONTENT_URI,
				new String[] { Groups.SOURCE_ID, }, Groups._ID + "=?",
				new String[] { String.valueOf(groupId) }, null);
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					sourceGroupId = cursor.getString(0);
				}
			} finally {
				cursor.close();
			}
		}
		return sourceGroupId;
	}

	/**
	 * Clear the local system 'dirty' flag for a contact.
	 * 
	 * @param context
	 *            the Authenticator Activity context
	 * @param rawContactId
	 *            the id of the contact update
	 * @param batchOperation
	 *            allow us to batch together multiple operations
	 */
	private static void clearDirtyFlag(long rawContactId,
			BatchOperation batchOperation) {
		final ContactOperations contactOp = ContactOperations
				.updateExistingContact(rawContactId, true, batchOperation);

		final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
				rawContactId);
		contactOp.updateDirtyFlag(false, uri);
	}

	private static void setGroupSourceId(long groupId, String sourceId,
			BatchOperation batchOperation) {
		GroupOperations groupOp = GroupOperations.updateExistingGroup(true,
				batchOperation);
		final Uri uri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
		groupOp.updateSourceId(sourceId, uri);
	}

	private static void clearGroupDirtyFlag(long rawGroupId,
			BatchOperation batchOperation) {
		final GroupOperations groupOp = GroupOperations.updateExistingGroup(
				true, batchOperation);
		final Uri uri = ContentUris.withAppendedId(Groups.CONTENT_URI,
				rawGroupId);
		groupOp.updateDirtyFlag(false, uri);
	}

	/**
	 * Deletes a contact from the platform contacts provider. This method is
	 * used both for contacts that were deleted locally and then that deletion
	 * was synced to the server, and for contacts that were deleted on the
	 * server and the deletion was synced to the client.
	 * 
	 * @param rawContactId
	 *            the unique Id for this rawContact in contacts provider
	 */
	private static void deleteContact(Context context, long rawContactId,
			BatchOperation batchOperation, String accountName) {

		batchOperation.add(ContactOperations.newDeleteCpo(
				ContentUris.withAppendedId(RawContacts.CONTENT_URI,
						rawContactId), true, true).build());

		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(DataQuery.CONTENT_URI,
				DataQuery.PROJECTION, DataQuery.SELECTION_TYPE,
				new String[] { String.valueOf(rawContactId),
						Photo.CONTENT_ITEM_TYPE }, null);
		while (c.moveToNext()) {
			if (!c.isNull(DataQuery.COLUMN_SYNC3)) {
				String fileName = c.getString(DataQuery.COLUMN_SYNC3);
				// Delete old photo file.
				File photoFile = PhotoHelper.getPhotoFile(context, fileName,
						accountName);
				if (photoFile.exists()) {
					boolean deleted = photoFile.delete();
					if (!deleted) {
						LogHelper.logW(TAG, "Photo File could not be deleted:"
								+ photoFile.getAbsolutePath());
					}
				}
			}
		}
	}

	private static void deleteContactGroup(long rawContactGroupId,
			BatchOperation batchOperation) {

		batchOperation.add(GroupOperations.newDeleteCpo(
				ContentUris.withAppendedId(Groups.CONTENT_URI,
						rawContactGroupId), true, true).build());
	}

	private static void deleteMyServerId(long rawContactId,
			BatchOperation batchOperation) {
		final ContactOperations contactOp = ContactOperations
				.updateExistingContact(rawContactId, true, batchOperation);

		final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
				rawContactId);
		contactOp.updateMyServerId(null, uri);
	}

	public static final class EditorQuery {

		private EditorQuery() {
		}

		public static final String[] PROJECTION = new String[] {
				RawContacts.ACCOUNT_NAME, Data._ID, RawContacts.Entity.DATA_ID,
				Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA15,
				Data.SYNC1 };

		public static final int COLUMN_ACCOUNT_NAME = 0;
		public static final int COLUMN_RAW_CONTACT_ID = 1;
		public static final int COLUMN_DATA_ID = 2;
		public static final int COLUMN_MIMETYPE = 3;
		public static final int COLUMN_DATA1 = 4;
		public static final int COLUMN_DATA2 = 5;
		public static final int COLUMN_DATA3 = 6;
		public static final int COLUMN_DATA15 = 7;
		public static final int COLUMN_SYNC1 = 8;

		public static final int COLUMN_PHONE_NUMBER = COLUMN_DATA1;
		public static final int COLUMN_PHONE_TYPE = COLUMN_DATA2;
		public static final int COLUMN_EMAIL_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_EMAIL_TYPE = COLUMN_DATA2;
		public static final int COLUMN_FULL_NAME = COLUMN_DATA1;
		public static final int COLUMN_GIVEN_NAME = COLUMN_DATA2;
		public static final int COLUMN_FAMILY_NAME = COLUMN_DATA3;
		public static final int COLUMN_AVATAR_IMAGE = COLUMN_DATA15;
		public static final int COLUMN_SYNC_DIRTY = COLUMN_SYNC1;

		public static final String SELECTION = Data.RAW_CONTACT_ID + "=?";
	}

	/**
	 * Constants for a query to find our contacts that are in need of syncing to
	 * the server. This should cover new, edited, and deleted contacts.
	 */
	private static final class DirtyQuery {

		private DirtyQuery() {
		}

		public static final String[] PROJECTION = new String[] {
				RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.DIRTY,
				RawContacts.DELETED, RawContacts.VERSION, RawContacts.SYNC3,
				RawContacts.SYNC2 };

		public static final int COLUMN_RAW_CONTACT_ID = 0;
		public static final int COLUMN_SERVER_ID = 1;
		public static final int COLUMN_DIRTY = 2;
		public static final int COLUMN_DELETED = 3;
		public static final int COLUMN_VERSION = 4;
		public static final int COLUMN_LASTVERSION = 5;
		public static final int COLUMN_CLIENTMOD = 6;

		public static final Uri CONTENT_URI = RawContacts.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();

		public static final String SELECTION = RawContacts.DIRTY + "=1 AND "
				+ RawContacts.ACCOUNT_TYPE + "='" + Constants.ACCOUNT_TYPE
				+ "' AND " + RawContacts.ACCOUNT_NAME + "=?";
	}

	private static final class DirtyGroupQuery {

		private DirtyGroupQuery() {
		}

		public static final String[] PROJECTION = new String[] { Groups._ID,
				Groups.SOURCE_ID, Groups.DIRTY, Groups.DELETED, Groups.VERSION };

		public static final int COLUMN_RAW_ID = 0;
		public static final int COLUMN_SERVER_ID = 1;
		public static final int COLUMN_DIRTY = 2;
		public static final int COLUMN_DELETED = 3;
		public static final int COLUMN_VERSION = 4;

		public static final Uri CONTENT_URI = Groups.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();

		public static final String SELECTION = Groups.DIRTY + "=1 AND "
				+ Groups.ACCOUNT_TYPE + "='" + Constants.ACCOUNT_TYPE
				+ "' AND " + Groups.ACCOUNT_NAME + "=?";
	}

	/**
	 * Constants for a query to all our contacts.
	 */
	private static final class RawContactAllQuery {

		private RawContactAllQuery() {
		}

		public static final String[] PROJECTION = new String[] { RawContacts._ID };

		public static final int COLUMN_RAW_CONTACT_ID = 0;

		public static final Uri CONTENT_URI = RawContacts.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();

		public static final String SELECTION = RawContacts.ACCOUNT_TYPE + "='"
				+ Constants.ACCOUNT_TYPE + "' AND " + RawContacts.ACCOUNT_NAME
				+ "=?";

		public static final String SELECTION_NOTDELETED = SELECTION + " AND "
				+ RawContacts.DELETED + " !=1";

		public static final String SELECTION_NOTDIRTY = SELECTION + " AND "
				+ RawContacts.DIRTY + " !=1";
	}

	/**
	 * Constants for a query Contact with a new Id (=ServerRowId set) After
	 * synchronization ServerRowId will be deleted.
	 */
	private static final class NewIdQuery {

		private NewIdQuery() {
		}

		public static final String[] PROJECTION = new String[] {
				RawContacts._ID, RawContacts.SOURCE_ID };

		public static final int COLUMN_RAW_CONTACT_ID = 0;

		public static final int COLUMN_SERVER_ID = 1;

		public static final Uri CONTENT_URI = RawContacts.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();

		public static final String SELECTION = RawContacts.ACCOUNT_TYPE + "='"
				+ Constants.ACCOUNT_TYPE + "' AND " + RawContacts.ACCOUNT_NAME
				+ "=? AND " + RawContacts.SYNC1 + " is not null";
	}

	/**
	 * Constants for a query to get contact data for a given rawContactId
	 */
	private static final class DataQuery {

		private DataQuery() {
		}

		public static final String[] PROJECTION = new String[] { Data._ID,
				Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA15,
				Data.IS_SUPER_PRIMARY, Data.DATA5, Data.DATA4, Data.DATA6,
				Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
				Data.DATA_VERSION, Data.SYNC1, Data.SYNC2, Data.SYNC3,
				Data.DATA14, Data.IS_PRIMARY };

		public static final int COLUMN_ID = 0;
		public static final int COLUMN_MIMETYPE = 1;
		public static final int COLUMN_DATA1 = 2;
		public static final int COLUMN_DATA2 = 3;
		public static final int COLUMN_DATA3 = 4;
		public static final int COLUMN_DATA15 = 5;
		public static final int COLUMN_IS_SUPER_PRIMARY = 6;
		public static final int COLUMN_DATA5 = 7;
		public static final int COLUMN_DATA4 = 8;
		public static final int COLUMN_DATA6 = 9;
		public static final int COLUMN_DATA7 = 10;
		public static final int COLUMN_DATA8 = 11;
		public static final int COLUMN_DATA9 = 12;
		public static final int COLUMN_DATA10 = 13;
		public static final int COLUMN_VERSION = 14;
		public static final int COLUMN_SYNC1 = 15;
		public static final int COLUMN_SYNC2 = 16;
		public static final int COLUMN_SYNC3 = 17;
		public static final int COLUMN_DATA14 = 18;
		public static final int COLUMN_IS_PRIMARY = 19;

		public static final Uri CONTENT_URI = Data.CONTENT_URI;

		public static final int COLUMN_PHONE_NUMBER = COLUMN_DATA1;
		public static final int COLUMN_PHONE_TYPE = COLUMN_DATA2;
		public static final int COLUMN_PHONE_LABEL = COLUMN_DATA3;
		public static final int COLUMN_EMAIL_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_EMAIL_TYPE = COLUMN_DATA2;
		public static final int COLUMN_EMAIL_LABEL = COLUMN_DATA3;
		public static final int COLUMN_WEBSITE_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_WEBSITE_TYPE = COLUMN_DATA2;
		public static final int COLUMN_WEBSITE_LABEL = COLUMN_DATA3;
		public static final int COLUMN_EVENT_DATA = COLUMN_DATA1;
		public static final int COLUMN_EVENT_TYPE = COLUMN_DATA2;
		public static final int COLUMN_EVENT_LABEL = COLUMN_DATA3;
		public static final int COLUMN_RELATION_DATA = COLUMN_DATA1;
		public static final int COLUMN_RELATION_TYPE = COLUMN_DATA2;
		public static final int COLUMN_RELATION_LABEL = COLUMN_DATA3;
		public static final int COLUMN_NICKNAME_DATA = COLUMN_DATA1;
		public static final int COLUMN_NICKNAME_TYPE = COLUMN_DATA2;
		public static final int COLUMN_NICKNAME_LABEL = COLUMN_DATA3;
		public static final int COLUMN_NOTE = COLUMN_DATA1;

		public static final int COLUMN_POSTAL_TYPE = COLUMN_DATA2;
		public static final int COLUMN_POSTAL_LABEL = COLUMN_DATA3;
		public static final int COLUMN_POSTAL_STREET = COLUMN_DATA4;
		public static final int COLUMN_POSTAL_POBOX = COLUMN_DATA5;
		public static final int COLUMN_POSTAL_NEIGHBORHOOD = COLUMN_DATA6;
		public static final int COLUMN_POSTAL_CITY = COLUMN_DATA7;
		public static final int COLUMN_POSTAL_REGION = COLUMN_DATA8;
		public static final int COLUMN_POSTAL_POSTCODE = COLUMN_DATA9;
		public static final int COLUMN_POSTAL_COUNTRY = COLUMN_DATA10;

		public static final int COLUMN_IM_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_IM_TYPE = COLUMN_DATA2;
		public static final int COLUMN_IM_LABEL = COLUMN_DATA3;
		public static final int COLUMN_IM_PROTOCOL_TYPE = COLUMN_DATA5;
		public static final int COLUMN_IM_PROTOCOL_NAME = COLUMN_DATA6;

		public static final int COLUMN_ORGANIZATION_NAME = COLUMN_DATA1;
		public static final int COLUMN_ORGANIZATION_TYPE = COLUMN_DATA2;
		public static final int COLUMN_ORGANIZATION_LABEL = COLUMN_DATA3;
		public static final int COLUMN_ORGANIZATION_TITLE = COLUMN_DATA4;
		public static final int COLUMN_ORGANIZATION_DEPARTMENT = COLUMN_DATA5;
		public static final int COLUMN_ORGANIZATION_JOBTITLE = COLUMN_DATA6;

		public static final int COLUMN_FULL_NAME = COLUMN_DATA1;
		public static final int COLUMN_GIVEN_NAME = COLUMN_DATA2;
		public static final int COLUMN_FAMILY_NAME = COLUMN_DATA3;
		public static final int COLUMN_PREFIX_NAME = COLUMN_DATA4;
		public static final int COLUMN_MIDDLE_NAME = COLUMN_DATA5;
		public static final int COLUMN_SUFFIX_NAME = COLUMN_DATA6;
		public static final int COLUMN_PHONECTIC_GIVEN = COLUMN_DATA7;
		public static final int COLUMN_PHONECTIC_MIDDLE = COLUMN_DATA8;
		public static final int COLUMN_PHONECTIC_LAST = COLUMN_DATA9;

		public static final int COLUMN_PHOTO_FILE_ID = COLUMN_DATA14;
		public static final int COLUMN_PHOTO_IMAGE = COLUMN_DATA15;

		public static final int COLUMN_GROUP_ROWID = COLUMN_DATA1;

		public static final String SELECTION = Data.RAW_CONTACT_ID + "=?";

		public static final String SELECTION_TYPE = Data.RAW_CONTACT_ID
				+ "=? and " + Data.MIMETYPE + "=?";
	}

	/**
	 * Constants for a query to read basic contact columns
	 */
	public static final class RawContactQuery {
		private RawContactQuery() {
		}

		public static final Uri CONTENT_URI = RawContacts.CONTENT_URI;

		public static final String[] PROJECTION_ID = new String[] { RawContacts._ID };

		public static final String[] PROJECTION_FULL = new String[] {
				RawContacts._ID, RawContacts.STARRED,
				RawContacts.SEND_TO_VOICEMAIL, RawContacts.CUSTOM_RINGTONE,
				RawContacts.CONTACT_ID, RawContacts.SYNC2, RawContacts.VERSION,
				RawContacts.SYNC3 };

		public static final int COLUMN_ID = 0;

		public static final int COLUMN_STARRED = 1;

		public static final int COLUMN_SEND_TO_VOICEMAIL = 2;

		public static final int COLUMN_CUSTOM_RINGTONE = 3;

		public static final int COLUMN_CONTACT_ID = 4;

		public static final int COLUMN_LASTMOD = 5;

		public static final int COLUMN_VERSION = 6;

		public static final int COLUMN_CLIENT_VERSION = 7;

		public static final String SELECTION = RawContacts._ID + "=?";

		public static final String SERVER_SELECTION = RawContacts.SOURCE_ID
				+ "=?";

		public static final String VERSION_SELECTION = RawContacts._ID
				+ "=? AND " + RawContacts.VERSION + "=?";
	}

	/**
	 * Constants for a query to read basic contact columns of one Group
	 */
	public static final class GroupQuery {
		private GroupQuery() {
		}

		public static final Uri CONTENT_URI = Groups.CONTENT_URI;

		public static final String[] PROJECTION = new String[] { Groups._ID };

		public static final int COLUMN_ID = 0;

		public static final String SELECTION = Groups.SOURCE_ID + "=? AND "
				+ Groups.ACCOUNT_TYPE + "='" + Constants.ACCOUNT_TYPE
				+ "' AND " + Groups.ACCOUNT_NAME + "=?";

		public static final String SELECTION_ALL = Groups.ACCOUNT_TYPE + "='"
				+ Constants.ACCOUNT_TYPE + "' AND " + Groups.ACCOUNT_NAME
				+ "=?";

		public static final String SELECTION_NOTDELETED = SELECTION_ALL
				+ " AND " + Groups.DELETED + " !=1";

		public static final String SELECTION_NOTDIRTY = SELECTION_ALL + " AND "
				+ Groups.DIRTY + " !=1";

		public static final String VERSION_SELECTION = Groups._ID + "=? AND "
				+ Groups.VERSION + "=?";
	}

	private static final class GroupAllQuery {

		private GroupAllQuery() {
		}

		public static final String[] PROJECTION = new String[] { Groups._ID,
				Groups.TITLE, Groups.NOTES, Groups.SOURCE_ID, Groups.VERSION };

		public static final int COLUMN_ID = 0;
		public static final int COLUMN_TITLE = 1;
		public static final int COLUMN_NOTES = 2;
		public static final int COLUMN_SOURCEID = 3;
		public static final int COLUMN_VERSION = 4;

		public static final Uri CONTENT_URI = Groups.CONTENT_URI;

		public static final String SELECTION = Groups._ID + "=?";
	}
}
