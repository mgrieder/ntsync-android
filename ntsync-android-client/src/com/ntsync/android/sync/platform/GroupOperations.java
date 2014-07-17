package com.ntsync.android.sync.platform;

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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;

import com.ntsync.android.sync.shared.Constants;

public class GroupOperations {
	private final ContentValues mValues;
	private final BatchOperation mBatchOperation;
	private boolean mIsSyncOperation;

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

	/**
	 * Returns an instance of GroupOperations instance for adding new groups to
	 * the platform contacts provider.
	 * 
	 * @param serverRowId
	 *            Server Row Id
	 * @param accountName
	 *            the username for the SyncAdapter account
	 * @param isSyncOperation
	 *            are we executing this as part of a sync operation?
	 * @return instance of ContactOperations
	 */
	public static GroupOperations createNewGroup(String serverGroupId,
			String accountName, String title, String notes,
			boolean isSyncOperation, BatchOperation batchOperation) {
		return new GroupOperations(serverGroupId, accountName, title, notes,
				isSyncOperation, batchOperation);
	}

	/**
	 * Returns an instance of GroupOperations for updating existing Groups in
	 * the platform contacts provider.
	 * 
	 * @param isSyncOperation
	 *            are we executing this as part of a sync operation?
	 * @return instance of GroupOperations
	 */
	public static GroupOperations updateExistingGroup(boolean isSyncOperation,
			BatchOperation batchOperation) {
		return new GroupOperations(isSyncOperation, batchOperation);
	}

	public GroupOperations(boolean isSyncOperation,
			BatchOperation batchOperation) {
		mValues = new ContentValues();
		mIsYieldAllowed = true;
		mIsSyncOperation = isSyncOperation;
		mBatchOperation = batchOperation;
	}

	public GroupOperations(String sourceId, String accountName, String title,
			String notes, boolean isSyncOperation, BatchOperation batchOperation) {
		this(isSyncOperation, batchOperation);
		mValues.put(Groups.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		mValues.put(Groups.ACCOUNT_NAME, accountName);

		if (!TextUtils.isEmpty(title)) {
			mValues.put(Groups.TITLE, title);
		}

		if (!TextUtils.isEmpty(notes)) {
			mValues.put(Groups.NOTES, notes);
		}

		if (!TextUtils.isEmpty(sourceId)) {
			mValues.put(Groups.SOURCE_ID, sourceId);
		}

		ContentProviderOperation.Builder builder = newInsertCpo(
				Groups.CONTENT_URI, mIsSyncOperation, true).withValues(mValues);
		mBatchOperation.add(builder.build());
	}

	public GroupOperations updateDirtyFlag(boolean isDirty, Uri uri) {
		int isDirtyValue = isDirty ? 1 : 0;
		mValues.clear();
		mValues.put(Groups.DIRTY, isDirtyValue);
		addUpdateOp(uri);
		return this;
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

	public GroupOperations updateGroup(Uri uri, String existingTitle,
			String existingNotes, String title, String notes) {

		mValues.clear();
		if (!TextUtils.equals(existingTitle, title)) {
			mValues.put(Groups.TITLE, title);
		}
		if (!TextUtils.equals(existingNotes, notes)) {
			mValues.put(Groups.NOTES, notes);
		}
		if (mValues.size() > 0) {
			addUpdateOp(uri);
		}
		return this;
	}

	public GroupOperations updateSourceId(String sourceId, Uri uri) {
		mValues.clear();
		if (TextUtils.isEmpty(sourceId)) {
			mValues.putNull(Groups.SOURCE_ID);
		} else {
			mValues.put(Groups.SOURCE_ID, sourceId);
		}
		addUpdateOp(uri);
		return this;
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