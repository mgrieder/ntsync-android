
package com.ntsync.android.sync.platform;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on ContactOperations.java from the SampleSyncAdapter-Example in Android SDK
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;

/**
 * Helper class for storing data in the platform content providers.
 */
public class ContactOperations {
	private final ContentValues mValues;
	private final BatchOperation mBatchOperation;
	private boolean mIsSyncOperation;
	private long mRawContactId;
	private int mBackReference;
	private boolean mIsNewContact;

	private static final String TAG = "ContactOperations";

	/**
	 * Since we're sending a lot of contact provider operations in a single
	 * batched operation, we want to make sure that we "yield" periodically so
	 * that the Contact Provider can write changes to the DB, and can open a new
	 * transaction. This prevents ANR (application not responding) errors. The
	 * recommended time to specify that a yield is permitted is with the first
	 * operation on a particular contact. So if we're updating multiple fields
	 * for a single contact, we make sure that we call withYieldAllowed(true) on
	 * the first field that we update. We use mIsYieldAllowed to keep track of
	 * what value we should pass to withYieldAllowed().
	 */
	private boolean mIsYieldAllowed;

	private static final int COMPRESSION_THUMBNAIL_LOW = 90;

	/**
	 * Returns an instance of ContactOperations instance for adding new contact
	 * to the platform contacts provider.
	 * 
	 * @param serverRowId
	 *            Server Row Id
	 * @param accountName
	 *            the username for the SyncAdapter account
	 * @param isSyncOperation
	 *            are we executing this as part of a sync operation?
	 * @return instance of ContactOperations
	 */
	public static ContactOperations createNewContact(String serverRowId,
			String accountName, boolean starred, String customRingTone,
			boolean sendToVoiceMail, Date clientModDate,
			boolean isSyncOperation, BatchOperation batchOperation) {
		return new ContactOperations(serverRowId, accountName, starred,
				customRingTone, sendToVoiceMail, clientModDate,
				isSyncOperation, batchOperation);
	}

	/**
	 * Returns an instance of ContactOperations for updating existing contact in
	 * the platform contacts provider.
	 * 
	 * @param rawContactId
	 *            the unique Id of the existing rawContact
	 * @param isSyncOperation
	 *            are we executing this as part of a sync operation?
	 * @return instance of ContactOperations
	 */
	public static ContactOperations updateExistingContact(long rawContactId,
			boolean isSyncOperation, BatchOperation batchOperation) {
		return new ContactOperations(rawContactId, isSyncOperation,
				batchOperation);
	}

	public ContactOperations(boolean isSyncOperation,
			BatchOperation batchOperation) {
		mValues = new ContentValues();
		mIsYieldAllowed = true;
		mIsSyncOperation = isSyncOperation;
		mBatchOperation = batchOperation;
	}

	public ContactOperations(String serverRowId, String accountName,
			boolean starred, String customRingTone, boolean sendToVoiceMail,
			Date clientModDate, boolean isSyncOperation,
			BatchOperation batchOperation) {
		this(isSyncOperation, batchOperation);
		mBackReference = mBatchOperation.size();
		mIsNewContact = true;
		mValues.put(RawContacts.SOURCE_ID, serverRowId);
		if (!TextUtils.isEmpty(serverRowId)) {
			// RowId has to be synced to Server
			mValues.put(RawContacts.SYNC1, Boolean.toString(true));
		}
		mValues.put(RawContacts.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		mValues.put(RawContacts.ACCOUNT_NAME, accountName);
		if (clientModDate != null) {
			mValues.put(RawContacts.SYNC2, clientModDate.getTime());
		}
		// Assume Version 1 (will be validate again)
		mValues.put(RawContacts.SYNC3, 1);
		if (starred) {
			mValues.put(RawContacts.STARRED, 1);
		}
		if (sendToVoiceMail) {
			mValues.put(RawContacts.SEND_TO_VOICEMAIL, 1);
		}
		if (customRingTone != null) {
			mValues.put(RawContacts.CUSTOM_RINGTONE, customRingTone);
		}
		ContentProviderOperation.Builder builder = newInsertCpo(
				RawContacts.CONTENT_URI, mIsSyncOperation, true).withValues(
				mValues);
		mBatchOperation.add(builder.build());
	}

	public ContactOperations(long rawContactId, boolean isSyncOperation,
			BatchOperation batchOperation) {
		this(isSyncOperation, batchOperation);
		mIsNewContact = false;
		mRawContactId = rawContactId;
	}

	/**
	 * Adds a contact name. We can take either a full name ("Bob Smith") or
	 * separated first-name and last-name ("Bob" and "Smith").
	 * 
	 * @param fullName
	 *            The full name of the contact - typically from an edit form Can
	 *            be null if firstName/lastName are specified.
	 * @param firstName
	 *            The first name of the contact - can be null if fullName is
	 *            specified.
	 * @param lastName
	 *            The last name of the contact - can be null if fullName is
	 *            specified.
	 * @return instance of ContactOperations
	 */
	public ContactOperations addName(String fullName, String firstName,
			String lastName, String middleName, String suffixName,
			String prefixName, String phoneticFirst, String phonecticMiddle,
			String phonecticLast) {
		mValues.clear();

		mValues.put(StructuredName.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
		if (!TextUtils.isEmpty(fullName)) {
			mValues.put(StructuredName.DISPLAY_NAME, fullName);
		}
		if (!TextUtils.isEmpty(firstName)) {
			mValues.put(StructuredName.GIVEN_NAME, firstName);
		}
		if (!TextUtils.isEmpty(lastName)) {
			mValues.put(StructuredName.FAMILY_NAME, lastName);
		}
		if (!TextUtils.isEmpty(middleName)) {
			mValues.put(StructuredName.MIDDLE_NAME, middleName);
		}
		if (!TextUtils.isEmpty(suffixName)) {
			mValues.put(StructuredName.SUFFIX, suffixName);
		}
		if (!TextUtils.isEmpty(prefixName)) {
			mValues.put(StructuredName.PREFIX, prefixName);
		}
		if (!TextUtils.isEmpty(phonecticLast)) {
			mValues.put(StructuredName.PHONETIC_FAMILY_NAME, phonecticLast);
		}
		if (!TextUtils.isEmpty(phoneticFirst)) {
			mValues.put(StructuredName.PHONETIC_GIVEN_NAME, phoneticFirst);
		}
		if (!TextUtils.isEmpty(phonecticMiddle)) {
			mValues.put(StructuredName.PHONETIC_MIDDLE_NAME, phonecticMiddle);
		}

		if (mValues.size() > 1) {
			addInsertOp();
		}
		return this;
	}

	/**
	 * Adds an email
	 * 
	 * @param the
	 *            email address we're adding
	 * @return instance of ContactOperations
	 */
	public ContactOperations addEmail(String email, int emailType,
			String label, boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(email)) {
			mValues.put(Email.DATA, email);
			mValues.put(Email.TYPE, emailType);
			mValues.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Email.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}
			addInsertOp();
		}
		return this;
	}

	/**
	 * Adds a phone number
	 * 
	 * @param phone
	 *            new phone number for the contact
	 * @param phoneType
	 *            the type: cell, home, etc.
	 * @return instance of ContactOperations
	 */
	public ContactOperations addPhone(String phone, int phoneType,
			String label, boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(phone)) {
			mValues.put(Phone.NUMBER, phone);
			mValues.put(Phone.TYPE, phoneType);
			mValues.put(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Phone.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}

			addInsertOp();
		}
		return this;
	}

	public ContactOperations addWebsite(String website, int type, String label,
			boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(website)) {
			mValues.put(Website.URL, website);
			mValues.put(Website.TYPE, type);
			mValues.put(Website.MIMETYPE, Website.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Website.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}

			addInsertOp();
		}
		return this;
	}

	public ContactOperations addNickname(String nickname, int type,
			String label, boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(nickname)) {
			mValues.put(Nickname.NAME, nickname);
			mValues.put(Nickname.TYPE, type);
			mValues.put(Nickname.MIMETYPE, Nickname.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Nickname.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}

			addInsertOp();
		}
		return this;
	}

	public ContactOperations addEvent(String event, int type, String label,
			boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(event)) {
			mValues.put(Event.START_DATE, event);
			mValues.put(Event.TYPE, type);
			mValues.put(Event.MIMETYPE, Event.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Event.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}

			addInsertOp();
		}
		return this;
	}

	public ContactOperations addNote(String note) {
		mValues.clear();
		if (!TextUtils.isEmpty(note)) {
			mValues.put(Note.NOTE, note);
			mValues.put(Note.MIMETYPE, Note.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}

	public ContactOperations addGroupMembership(String groupSourceId) {
		mValues.clear();
		if (!TextUtils.isEmpty(groupSourceId)) {
			mValues.put(GroupMembership.GROUP_SOURCE_ID, groupSourceId);
			mValues.put(GroupMembership.MIMETYPE,
					GroupMembership.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}

	/**
	 * Adds a group membership
	 * 
	 * @param id
	 *            The id of the group to assign
	 * @return instance of ContactOperations
	 */
	public ContactOperations addGroupMembership(Long groupId) {
		if (groupId != null) {
			mValues.clear();
			mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
			mValues.put(GroupMembership.MIMETYPE,
					GroupMembership.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}

	public ContactOperations addIm(String data, int type, String label,
			boolean isPrimary, boolean isSuperPrimary, int imProtocolType,
			String customProtocolName) {
		mValues.clear();
		if (!TextUtils.isEmpty(data)) {
			mValues.put(Im.DATA, data);
			mValues.put(Im.TYPE, type);
			mValues.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Im.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}
			mValues.put(Im.PROTOCOL, imProtocolType);
			if (!TextUtils.isEmpty(customProtocolName)) {
				mValues.put(Im.CUSTOM_PROTOCOL, customProtocolName);
			}

			addInsertOp();
		}
		return this;
	}

	public ContactOperations addOrganization(String data, int type,
			String label, boolean isPrimary, boolean isSuperPrimary,
			String title, String department, String jobDescription) {
		mValues.clear();
		if (!TextUtils.isEmpty(data)) {
			mValues.put(Organization.DATA, data);
			mValues.put(Organization.TYPE, type);
			mValues.put(Organization.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Organization.LABEL, label);
			}
			if (!TextUtils.isEmpty(title)) {
				mValues.put(Organization.TITLE, title);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}
			mValues.put(Organization.DEPARTMENT, department);
			if (!TextUtils.isEmpty(jobDescription)) {
				mValues.put(Organization.JOB_DESCRIPTION, jobDescription);
			}

			addInsertOp();
		}
		return this;

	}

	public ContactOperations addRelation(String data, int type, String label,
			boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		if (!TextUtils.isEmpty(data)) {
			mValues.put(Relation.DATA, data);
			mValues.put(Relation.TYPE, type);
			mValues.put(Relation.MIMETYPE, Relation.CONTENT_ITEM_TYPE);
			if (!TextUtils.isEmpty(label)) {
				mValues.put(Relation.LABEL, label);
			}
			if (isSuperPrimary) {
				mValues.put(Email.IS_SUPER_PRIMARY, 1);
			}
			if (isPrimary) {
				mValues.put(Email.IS_PRIMARY, 1);
			}
			addInsertOp();
		}
		return this;
	}

	public ContactOperations addAddress(String city, String country,
			String label, int androidAddressType, String neighborhood,
			String pobox, String postcode, String region, String street,
			boolean isPrimary, boolean isSuperPrimary) {
		mValues.clear();
		mValues.put(StructuredPostal.CITY, city);
		mValues.put(StructuredPostal.COUNTRY, country);
		mValues.put(StructuredPostal.MIMETYPE,
				StructuredPostal.CONTENT_ITEM_TYPE);
		if (!TextUtils.isEmpty(label)) {
			mValues.put(StructuredPostal.LABEL, label);
		}

		if (isSuperPrimary) {
			mValues.put(Email.IS_SUPER_PRIMARY, 1);
		}
		if (isPrimary) {
			mValues.put(Email.IS_PRIMARY, 1);
		}
		mValues.put(StructuredPostal.NEIGHBORHOOD, neighborhood);
		mValues.put(StructuredPostal.POBOX, pobox);
		mValues.put(StructuredPostal.POSTCODE, postcode);
		mValues.put(StructuredPostal.REGION, region);
		mValues.put(StructuredPostal.STREET, street);

		addInsertOp();
		return this;
	}

	public ContactOperations addPhoto(byte[] avatar, Context context,
			String accountName, boolean isSuperPrimary) throws IOException {
		boolean insert = savePhoto(avatar, context, accountName, null, 0,
				false, isSuperPrimary);
		if (insert) {
			addInsertOp();
		}
		return this;
	}

	/**
	 * Clears metadata about the original photo file.
	 * 
	 * @param uri
	 */
	public ContactOperations clearPhotoMetadata(Uri uri) {
		mValues.clear();
		mValues.putNull(Photo.SYNC1);
		mValues.putNull(Photo.SYNC2);
		mValues.putNull(Photo.SYNC3);
		addUpdateOp(uri);
		return this;
	}

	private boolean savePhoto(byte[] photo, Context context,
			String accountName, String existingFilename, int syncVersion,
			boolean update, boolean isSuperPrimary) throws IOException {
		// Scale down to prevent ContentValues size limit restriction.
		byte[] contactPhoto = photo != null && photo.length > 0 ? photo : null;
		if (contactPhoto != null) {
			Bitmap map = BitmapFactory.decodeByteArray(photo, 0, photo.length);
			if (map != null) {
				int size = getDisplaySize(context);
				if (Log.isLoggable(TAG, Log.INFO)) {
					Log.i(TAG,
							"save Photo size: " + map.getWidth() + "x"
									+ map.getHeight() + " displaySize:" + size);
				}
				Bitmap normMap = PhotoHelper.getNormalizedBitmap(map, size);
				if (normMap != map && normMap != null) {
					// Write scaled version
					contactPhoto = PhotoHelper.getCompressedBytes(normMap,
							COMPRESSION_THUMBNAIL_LOW);
				}
			}
		}
		boolean updateDB = false;

		boolean saveOrgFile = contactPhoto != photo && photo != null
				&& photo.length > 0;

		if (update || contactPhoto != null) {
			// Write Display-Photo
			mValues.clear();
			mValues.put(Photo.PHOTO, contactPhoto);
			mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
			if (isSuperPrimary) {
				mValues.put(Photo.IS_PRIMARY, 1);
				mValues.put(Photo.IS_SUPER_PRIMARY, 1);
			} else if (update) {
				mValues.put(Photo.IS_PRIMARY, 0);
				mValues.put(Photo.IS_SUPER_PRIMARY, 0);
			}

			// Hash-Value
			mValues.putNull(Photo.SYNC1);
			// Save Sync-Version (this modifications increments the version)
			int nextVersion = syncVersion + 1;
			mValues.put(Photo.SYNC2, nextVersion);
			UUID orgFilename = UUID.randomUUID();

			if (saveOrgFile) {
				// Org-Filename
				mValues.put(Photo.SYNC3, orgFilename.toString());
			} else {
				mValues.putNull(Photo.SYNC3);
			}
			if (contactPhoto != null) {
				mBatchOperation.addBlobSize(contactPhoto.length);
			}
			updateDB = true;

			if (saveOrgFile) {
				File photoFile = PhotoHelper.getPhotoFile(context,
						orgFilename.toString(), accountName);
				FileOutputStream st = new FileOutputStream(photoFile);
				try {
					st.write(photo);
					st.close();
				} finally {
					st.close();
				}
			}
		}

		// Delete old file
		if (existingFilename != null) {
			// Cleanup. Delete old photo file.
			File photoFile = PhotoHelper.getPhotoFile(context,
					existingFilename, accountName);
			if (photoFile.exists()) {
				boolean deleted = photoFile.delete();
				if (!deleted) {
					LogHelper.logW(TAG, "PhotoFile could not be deleted: "
							+ photoFile.getPath());
				}
			}
		}
		return updateDB;
	}

	public ContactOperations updatePhotoHash(String hash, int version, Uri uri) {
		mValues.clear();
		// Hash
		mValues.put(Photo.SYNC1, hash);
		// Save Sync-Version (this modifications increments the version)
		mValues.put(Photo.SYNC2, version);
		addUpdateOp(uri);
		return this;
	}

	private int getDisplaySize(Context context) {
		int dim;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			dim = 96;
		} else {
			dim = ICSPhotoHelper.getDisplayPhotoSize(context);
		}
		return dim;
	}

	/**
	 * Updates contact's serverId
	 * 
	 * @param serverId
	 *            the serverId for this contact
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateServerId(String serverId, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SOURCE_ID, serverId);
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Updates temporary server Id which is not yet synchronized to the server.
	 * 
	 * @param serverId
	 *            the serverId for this contact
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateMyServerId(String serverId, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SYNC1, serverId);
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Update the custom row-modification-date
	 * 
	 * @param serverId
	 *            the serverId for this contact
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateClientMod(Long version, Long clientMod,
			Uri uri) {
		mValues.clear();
		if (clientMod != null) {
			mValues.put(RawContacts.SYNC2, clientMod);
		}
		mValues.put(RawContacts.SYNC3, version);
		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "ClientMod updated: "
					+ (clientMod != null ? new Date(clientMod).toString()
							: "null") + ",version:" + version
					+ " for contactId:" + ContentUris.parseId(uri));
		}
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Updates contact's email
	 * 
	 * @param email
	 *            email id of the sample SyncAdapter user
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateEmail(String email, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Email.DATA, email);
		mValues.put(Email.LABEL, label);
		mValues.put(Email.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Email.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Updates contact's website
	 * 
	 * @param email
	 *            email id of the sample SyncAdapter user
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateWebsite(String website, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Website.DATA, website);
		mValues.put(Website.LABEL, label);
		mValues.put(Website.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Website.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateEvent(String event, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Event.START_DATE, event);
		mValues.put(Event.LABEL, label);
		mValues.put(Event.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Event.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateContact(boolean starred,
			boolean existingStarred, String customRingtone,
			String existingRingtone, boolean sendToVoicemail,
			boolean existingSendToVoicemail, long rawContactId, Long contactId,
			Date lastModification, long currVersion) {
		mValues.clear();

		boolean updateContact = false;
		if (starred != existingStarred) {
			mValues.put(RawContacts.STARRED, starred);
		}
		if (!TextUtils.equals(customRingtone, existingRingtone)) {
			mValues.put(RawContacts.CUSTOM_RINGTONE, customRingtone);
			updateContact = true;
		}
		if (sendToVoicemail != existingSendToVoicemail) {
			mValues.put(RawContacts.SEND_TO_VOICEMAIL, sendToVoicemail);
			updateContact = true;
		}
		if (lastModification != null) {
			mValues.put(RawContacts.SYNC2, lastModification.getTime());
			if (Log.isLoggable(TAG, Log.INFO)) {
				Log.i(TAG, "Update ClientMod. ContactId:" + rawContactId
						+ " ModDate:" + lastModification + " Version:"
						+ (currVersion + 1));
			}
		}
		mValues.put(RawContacts.SYNC3, currVersion + 1);
		Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
				rawContactId);
		addUpdateOp(uri);

		// ContactUpdate
		if (updateContact && contactId != null) {
			// CustomRingtone and Voice will be aggregated only for inserts,
			// Update at contact
			uri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
					contactId.longValue());
			mValues.put(Contacts.CUSTOM_RINGTONE, customRingtone);
			mValues.put(Contacts.SEND_TO_VOICEMAIL, sendToVoicemail);
			addUpdateOp(uri);
		}

		return this;
	}

	public ContactOperations updateRelation(String relation, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Relation.NAME, relation);
		mValues.put(Relation.LABEL, label);
		mValues.put(Relation.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Relation.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateNickname(String nickname, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Nickname.NAME, nickname);
		mValues.put(Nickname.LABEL, label);
		mValues.put(Nickname.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Nickname.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateIm(String imAddress, String label,
			boolean isPrimary, boolean isSuperPrimary, int proType,
			String customProName, Uri uri) {
		mValues.clear();
		mValues.put(Im.DATA, imAddress);
		mValues.put(Im.LABEL, label);
		mValues.put(Im.PROTOCOL, proType);
		mValues.put(Im.CUSTOM_PROTOCOL, customProName);
		mValues.put(Im.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Im.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateOrganization(String data, int type,
			String label, String title, String department,
			String jobDescription, boolean isPrimary, boolean isSuperPrimary,
			Uri uri) {
		mValues.clear();
		mValues.put(Organization.COMPANY, data);
		mValues.put(Organization.LABEL, label);
		mValues.put(Organization.TYPE, type);
		mValues.put(Organization.TITLE, title);
		mValues.put(Organization.DEPARTMENT, department);
		mValues.put(Organization.JOB_DESCRIPTION, jobDescription);
		mValues.put(Organization.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Organization.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	public ContactOperations updateAddress(String city, String country,
			String neighborhood, String pobox, String postcode, String region,
			String street, String label, boolean isPrimary,
			boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(StructuredPostal.CITY, city);
		mValues.put(StructuredPostal.COUNTRY, country);
		mValues.put(StructuredPostal.NEIGHBORHOOD, neighborhood);
		mValues.put(StructuredPostal.POBOX, pobox);
		mValues.put(StructuredPostal.POSTCODE, postcode);
		mValues.put(StructuredPostal.REGION, region);
		mValues.put(StructuredPostal.STREET, street);
		mValues.put(StructuredPostal.LABEL, label);

		mValues.put(StructuredPostal.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(StructuredPostal.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Updates contact's name. The caller can either provide first-name and
	 * last-name fields or a full-name field.
	 * 
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @param existingFirstName
	 *            the first name stored in provider
	 * @param existingLastName
	 *            the last name stored in provider
	 * @param existingFullName
	 *            the full name stored in provider
	 * @param firstName
	 *            the new first name to store
	 * @param lastName
	 *            the new last name to store
	 * @param fullName
	 *            the new full name to store
	 * @return instance of ContactOperations
	 */
	public ContactOperations updateName(Uri uri, String existingFirstName,
			String existingLastName, String existingFullName,
			String existingMiddleName, String existingPrefixName,
			String existingSuffixName, String existingPhonFirst,
			String existingPhonMiddle, String existingPhonLast,
			String firstName, String lastName, String fullName,
			String middleName, String prefixName, String suffixName,
			String phonecticFirst, String phonecticMiddle, String phonecticLast) {

		mValues.clear();
		if (!TextUtils.equals(existingFullName, fullName)) {
			mValues.put(StructuredName.DISPLAY_NAME, fullName);
		}
		if (!TextUtils.equals(existingFirstName, firstName)) {
			mValues.put(StructuredName.GIVEN_NAME, firstName);
		}
		if (!TextUtils.equals(existingLastName, lastName)) {
			mValues.put(StructuredName.FAMILY_NAME, lastName);
		}
		if (!TextUtils.equals(existingMiddleName, middleName)) {
			mValues.put(StructuredName.MIDDLE_NAME, middleName);
		}
		if (!TextUtils.equals(existingSuffixName, suffixName)) {
			mValues.put(StructuredName.SUFFIX, suffixName);
		}
		if (!TextUtils.equals(existingPrefixName, prefixName)) {
			mValues.put(StructuredName.PREFIX, prefixName);
		}
		if (!TextUtils.equals(existingPhonFirst, phonecticFirst)) {
			mValues.put(StructuredName.PHONETIC_GIVEN_NAME, phonecticFirst);
		}
		if (!TextUtils.equals(existingPhonMiddle, phonecticMiddle)) {
			mValues.put(StructuredName.PHONETIC_MIDDLE_NAME, phonecticMiddle);
		}
		if (!TextUtils.equals(existingPhonLast, phonecticLast)) {
			mValues.put(StructuredName.PHONETIC_FAMILY_NAME, phonecticLast);
		}

		if (mValues.size() > 0) {
			addUpdateOp(uri);
		}
		return this;
	}

	public ContactOperations updateDirtyFlag(boolean isDirty, Uri uri) {
		int isDirtyValue = isDirty ? 1 : 0;
		mValues.clear();
		mValues.put(RawContacts.DIRTY, isDirtyValue);
		addUpdateOp(uri);
		return this;
	}

	/**
	 * Updates contact's phone
	 * 
	 * @param phone
	 *            new phone number for the contact
	 * @param uri
	 *            Uri for the existing raw contact to be updated
	 * @return instance of ContactOperations
	 */
	public ContactOperations updatePhone(String phone, String label,
			boolean isPrimary, boolean isSuperPrimary, Uri uri) {
		mValues.clear();
		mValues.put(Phone.NUMBER, phone);
		mValues.put(Phone.LABEL, label);
		mValues.put(Phone.IS_PRIMARY, isPrimary ? 1 : 0);
		mValues.put(Phone.IS_SUPER_PRIMARY, isSuperPrimary ? 1 : 0);
		addUpdateOp(uri);

		return this;
	}

	/**
	 * Deletes an Data-Entry
	 * 
	 * @param uri
	 * @return
	 */
	public ContactOperations deleteData(Uri uri) {
		ContentProviderOperation.Builder builder = newDeleteCpo(uri,
				mIsSyncOperation, mIsYieldAllowed);
		mIsYieldAllowed = false;
		mBatchOperation.add(builder.build());
		return this;
	}

	/**
	 * 
	 * @param photo
	 * @param uri
	 * @param context
	 * @param accountName
	 * @param existingFileName
	 * @param syncVersion
	 * @param isSuperPrimary
	 * @return
	 * @throws FileNotFoundException
	 *             if Photo could be found.
	 * @throws IOException
	 *             IO-Error during update Photo
	 */
	public ContactOperations updatePhoto(byte[] photo, Uri uri,
			Context context, String accountName, String existingFileName,
			int syncVersion, boolean isSuperPrimary) throws IOException {

		savePhoto(photo, context, accountName, existingFileName, syncVersion,
				true, isSuperPrimary);
		addUpdateOp(uri);

		return this;
	}

	public ContactOperations updateNote(String note, Uri uri) {
		mValues.clear();
		mValues.put(Note.NOTE, note);
		mValues.put(Note.MIMETYPE, Note.CONTENT_ITEM_TYPE);
		addUpdateOp(uri);

		return this;
	}

	public ContactOperations updateGroupMembership(String groupSourceId, Uri uri) {
		mValues.clear();
		if (TextUtils.isEmpty(groupSourceId)) {
			mValues.putNull(GroupMembership.GROUP_ROW_ID);
		} else {
			mValues.put(GroupMembership.GROUP_SOURCE_ID, groupSourceId);
		}
		mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
		addUpdateOp(uri);

		return this;
	}

	public ContactOperations updateGroupMembership(Long groupId, Uri uri) {
		mValues.clear();
		mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
		mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
		addUpdateOp(uri);

		return this;
	}

	/**
	 * Adds an insert operation into the batch
	 */
	private void addInsertOp() {

		if (!mIsNewContact) {
			mValues.put(Phone.RAW_CONTACT_ID, mRawContactId);
		}
		ContentProviderOperation.Builder builder = newInsertCpo(
				Data.CONTENT_URI, mIsSyncOperation, mIsYieldAllowed);
		builder.withValues(mValues);
		if (mIsNewContact) {
			builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
		}
		mIsYieldAllowed = false;
		mBatchOperation.add(builder.build());
	}

	/**
	 * Adds an update operation into the batch
	 */
	private void addUpdateOp(Uri uri) {
		ContentProviderOperation.Builder builder = newUpdateCpo(uri,
				mIsSyncOperation, mIsYieldAllowed).withValues(mValues);
		mIsYieldAllowed = false;
		mBatchOperation.add(builder.build());
	}

	public static ContentProviderOperation.Builder newInsertCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newInsert(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}

	public static ContentProviderOperation.Builder newUpdateCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newUpdate(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}

	public static ContentProviderOperation.Builder newDeleteCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newDelete(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}

	private static Uri addCallerIsSyncAdapterParameter(Uri uri,
			boolean isSyncOperation) {
		if (isSyncOperation) {
			// If we're in the middle of a real sync-adapter operation, then go
			// ahead
			// and tell the Contacts provider that we're the sync adapter. That
			// gives us some special permissions - like the ability to really
			// delete a contact, and the ability to clear the dirty flag.
			//
			// If we're not in the middle of a sync operation (for example, we
			// just
			// locally created/edited a new contact), then we don't want to use
			// the special permissions, and the system will automagically mark
			// the contact as 'dirty' for us!
			return uri
					.buildUpon()
					.appendQueryParameter(
							ContactsContract.CALLER_IS_SYNCADAPTER, "true")
					.build();
		}
		return uri;
	}

}
