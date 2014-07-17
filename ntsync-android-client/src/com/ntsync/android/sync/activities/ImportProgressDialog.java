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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.DialogFragment;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.AccountInfo;
import com.ntsync.android.sync.platform.BatchOperation;
import com.ntsync.android.sync.platform.ContactManager;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.RawContact;

/**
 * Progress-Dialog and task for importing existing contacts to ntsync
 */
public class ImportProgressDialog extends DialogFragment {

	private static final String TAG = "ImportProgressDialog";

	private static final String PARAM_ACCOUNTINFO = "acInfo";
	private static final String PARAM_ACCOUNTNAME = "acName";
	private static final String PARAM_DELETELOCAL = "deleteLocal";

	private AsyncTask<Void, Integer, Exception> importTask = null;

	private AccountInfo acInfo;

	private boolean success = false;
	private boolean canceled = false;
	private ImportError taskError = null;
	private int imported = 0;
	private int available = 0;

	/**
	 * Creates a Import Dialog for importing all contacts
	 * 
	 * @param acInfo
	 *            null is allowed when all contacts should be imported
	 * @param accountName
	 *            accountName of one of our account-type.
	 * @return invisible Dialog
	 */
	public static ImportProgressDialog newInstance(AccountInfo acInfo,
			String accountName, boolean deleteLocalContacts) {
		ImportProgressDialog dlg = new ImportProgressDialog();

		Bundle args = new Bundle();
		args.putParcelable(PARAM_ACCOUNTINFO, acInfo);
		args.putString(PARAM_ACCOUNTNAME, accountName);
		args.putBoolean(PARAM_DELETELOCAL, deleteLocalContacts);

		dlg.setArguments(args);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		this.acInfo = args.getParcelable(PARAM_ACCOUNTINFO);
		String accountName = args.getString(PARAM_ACCOUNTNAME);
		boolean delLocalContacts = args.getBoolean(PARAM_DELETELOCAL);

		// Retain to keep Task during conf changes
		setRetainInstance(true);

		setCancelable(false);
		importTask = new ImportTask(acInfo, accountName, delLocalContacts);
		importTask.execute();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (importTask != null && importTask.getStatus() == Status.FINISHED) {
			deliverResult();
		}
	}

	/**
	 * Sends Dialog Result to Listener if Dialog is active, otherwise should be
	 * called again in onResume() when Task is finished
	 */
	protected void deliverResult() {
		// deliver result otherwise in onResume, when killed task will be
		// started again.
		if (isResumed()) {
			dismiss();
			if (resultListener != null) {
				resultListener.onCreateEnd(success, canceled, taskError,
						imported, available);
			}
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getText(R.string.import_activity_progress));
		dialog.setIndeterminate(false);
		if (acInfo != null) {
			dialog.setMax(acInfo.getContactCount());
		}
		return dialog;
	}

	/**
	 * The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 */
	public interface ImportDialogListener {
		/**
		 * 
		 * @param success
		 * @param cancel
		 * @param error
		 *            if success is false, contains an error
		 */
		void onCreateEnd(boolean success, boolean cancel, ImportError error,
				int importedCount, int availableCount);
	}

	public enum ImportError {
		DB_ERROR, IMPORT_FAILED
	}

	// Use this instance of the interface to deliver action events
	private ImportDialogListener resultListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			resultListener = (ImportDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			ClassCastException ex = new ClassCastException(activity.toString()
					+ " must implement ImportDialogListener");
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Importing the Contacts
	 */
	private class ImportTask extends AsyncTask<Void, Integer, Exception> {

		private AccountInfo acInfo;

		private int importedCount = 0;

		private int availableCount = 0;

		private String accountName;

		private boolean deleteLocalContacts;

		public ImportTask(AccountInfo acInfo, String accountName,
				boolean deleteLocalContacts) {
			this.acInfo = acInfo;
			this.accountName = accountName;
			this.deleteLocalContacts = deleteLocalContacts;
		}

		@Override
		protected Exception doInBackground(Void... params) {
			Exception ex = null;
			if (!isCancelled()) {
				try {
					doContactImport();
				} catch (IOException e) {
					LogHelper.logE(TAG, "Failed to store photo during import.",
							e);
					ex = e;
				} catch (OperationApplicationException e) {
					LogHelper.logE(TAG, "Failed to perform import.", e);
					ex = e;
				}
			}
			return ex;
		}

		private void doContactImport() throws OperationApplicationException,
				IOException {
			Context context = ImportProgressDialog.this.getActivity();
			ContentResolver resolver = ImportProgressDialog.this.getActivity()
					.getContentResolver();
			importContacts(context, resolver);
		}

		private void importContacts(Context context, ContentResolver resolver)
				throws OperationApplicationException, IOException {
			// Our ContactIds feststellen
			final Set<Long> existingIds = new HashSet<Long>();
			Cursor c = resolver.query(RawContacts.CONTENT_URI,
					new String[] { RawContacts.CONTACT_ID },
					RawContacts.ACCOUNT_TYPE + "='" + Constants.ACCOUNT_TYPE
							+ "' AND " + RawContacts.ACCOUNT_NAME + "=?",
					new String[] { accountName }, null);
			try {
				while (c.moveToNext()) {
					if (!c.isNull(0)) {
						existingIds.add(c.getLong(0));
					}
				}
			} finally {
				c.close();
			}

			Set<Long> contactIds = new HashSet<Long>();
			// Iterator over contacts and save to our account type, except
			// contacts
			// which are already available.
			String query = RawContacts.ACCOUNT_TYPE + "=? AND "
					+ RawContacts.ACCOUNT_NAME + "=?";
			String[] selArgs = new String[] { acInfo.accountType,
					acInfo.accountName };
			if (acInfo.accountType == null) {
				query = RawContacts.ACCOUNT_TYPE + " IS NULL AND "
						+ RawContacts.ACCOUNT_NAME + " IS NULL";
				selArgs = null;
			} else if (acInfo.accountName == null) {
				query = RawContacts.ACCOUNT_TYPE + "=? AND "
						+ RawContacts.ACCOUNT_NAME + " IS NULL";
				selArgs = new String[] { acInfo.accountType };
			}

			c = resolver.query(RawContacts.CONTENT_URI, new String[] {
					RawContacts._ID, RawContacts.CONTACT_ID }, query, selArgs,
					null);
			try {
				while (c.moveToNext()) {
					if (!c.isNull(1)) {
						Long contactId = c.getLong(1);
						Long rawContactId = c.getLong(0);
						if (!existingIds.contains(contactId)) {
							contactIds.add(rawContactId);
						}
					}
				}
			} finally {
				c.close();
			}
			if (isCancelled()) {
				return;
			}

			final int importCount = contactIds.size();
			availableCount = importCount;
			String photoAccountName = null;
			if (Constants.ACCOUNT_TYPE.equals(acInfo.accountType)) {
				// Settings Directory for Photo if its our type.
				photoAccountName = acInfo.accountName;
			}
			Map<Long, String> cachedGroupIds = new HashMap<Long, String>();
			List<RawContact> rawContacts = new ArrayList<RawContact>(6);
			BatchOperation batchOp = new BatchOperation(resolver);

			for (Iterator<Long> iterator = contactIds.iterator(); iterator
					.hasNext();) {
				Long rawContactId = iterator.next();
				RawContact rawContact = ContactManager.getRawContact(context,
						rawContactId, cachedGroupIds, photoAccountName,
						accountName, null, null);

				rawContacts.add(rawContact);
				// Prepare Delete
				if (deleteLocalContacts) {
					batchOp.add(ContentProviderOperation.newDelete(
							ContentUris.withAppendedId(RawContacts.CONTENT_URI,
									rawContactId)).build());
				}

				if (isCancelled()) {
					return;
				}
				if (rawContacts.size() >= 5) {
					// import only some contacts to prevent overuse of memory.
					ContactManager.updateContacts(context, accountName,
							rawContacts, false, null);
					importedCount += rawContacts.size();
					rawContacts.clear();
					batchOp.execute();

					this.publishProgress(importedCount, importCount);
				}
			}
			if (!rawContacts.isEmpty()) {
				ContactManager.updateContacts(context, accountName,
						rawContacts, false, null);
				importedCount += rawContacts.size();
				batchOp.execute();
			}

			this.publishProgress(importedCount, importCount);
		}

		@Override
		protected void onPostExecute(final Exception exception) {
			super.onPostExecute(exception);

			canceled = isCancelled();
			imported = importedCount;
			available = availableCount;
			if (exception == null) {
				success = true;
				taskError = null;
			} else {
				success = false;
				taskError = ImportError.IMPORT_FAILED;
				if (exception instanceof OperationApplicationException) {
					taskError = ImportError.DB_ERROR;
				}
			}
			deliverResult();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			Dialog dlg = ImportProgressDialog.this.getDialog();
			if (dlg != null && dlg.isShowing() && values.length > 1) {
				int max = values[1];
				ProgressDialog prg = (ProgressDialog) dlg;
				if (prg.getMax() != max) {
					prg.setMax(max);
				}
				prg.setProgress(values[0]);
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			success = false;
			imported = importedCount;
			available = availableCount;
			taskError = null;
			deliverResult();
		}
	}
}
