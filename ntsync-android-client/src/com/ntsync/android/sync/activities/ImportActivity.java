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
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.ImportProgressDialog.ImportDialogListener;
import com.ntsync.android.sync.activities.ImportProgressDialog.ImportError;
import com.ntsync.android.sync.platform.AccountInfo;
import com.ntsync.android.sync.platform.ContactManager;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.AbstractAsyncTaskLoader;

/**
 * Provides GUI for selecting a Import Source
 */
public class ImportActivity extends AbstractFragmentActivity implements
		OnItemSelectedListener, ImportDialogListener,
		LoaderCallbacks<List<AccountInfo>> {

	private boolean accountAvailable = false;
	private String accountName = null;

	private AccountAdapter sourceAccountAdapter;
	private ArrayAdapter<String> destAccountAdapter;

	private static final int LOADERID_ACCOUNTS = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_activity);

		// Init Source Account Spinner
		Spinner spinner = (Spinner) findViewById(R.id.sourceAccountSpinner);
		spinner.setOnItemSelectedListener(this);
		List<AccountInfo> entries = new ArrayList<AccountInfo>();
		sourceAccountAdapter = new AccountAdapter(this,
				R.layout.twoline_dropdown_item, entries);
		spinner.setAdapter(sourceAccountAdapter);

		// Init Destination Account Spinner
		spinner = (Spinner) findViewById(R.id.destAccountSpinner);
		spinner.setOnItemSelectedListener(this);
		destAccountAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item);
		destAccountAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(destAccountAdapter);

		initStartContactBtn();
	}

	@Override
	public void onResumeFragments() {
		super.onResumeFragments();

		updateDestinationAccountSelector();
		getSupportLoaderManager().initLoader(LOADERID_ACCOUNTS, null, this);
		updateBtnState();
	}

	private void updateDestinationAccountSelector() {
		AccountManager accountManager = AccountManager.get(this);
		Account[] accounts = accountManager
				.getAccountsByType(Constants.ACCOUNT_TYPE);
		accountAvailable = false;
		boolean clearAdapter = true;
		accountName = null;
		if (accounts.length > 0) {
			accountAvailable = true;
			if (accounts.length > 1) {
				// Account-Name Auswahl anzeigen
				clearAdapter = false;
				String[] names = new String[accounts.length];
				boolean changed = false;
				int oldCount = destAccountAdapter.getCount();
				for (int i = 0; i < accounts.length; i++) {
					names[i] = accounts[i].name;
					if (i >= oldCount
							|| !destAccountAdapter.getItem(i).equals(names[i])) {
						changed = true;
					}
				}
				if (changed) {
					destAccountAdapter.setNotifyOnChange(false);
					destAccountAdapter.clear();
					for (String name : names) {
						destAccountAdapter.add(name);
					}
					destAccountAdapter.setNotifyOnChange(true);
					destAccountAdapter.notifyDataSetChanged();
				}
			} else {
				accountName = accounts[0].name;
			}
		}
		if (clearAdapter) {
			destAccountAdapter.clear();
		}
	}

	private void setDestinationAccountSelector(boolean enabled) {
		int visState = enabled ? View.VISIBLE : View.GONE;
		findViewById(R.id.destAccountSpinner).setVisibility(visState);
		findViewById(R.id.destAccountTitle).setVisibility(visState);
	}

	private void setSourceAccountSelector(boolean enabled,
			boolean enableLocalCheckbox) {
		int visState = enabled ? View.VISIBLE : View.GONE;
		int localCheckboxState = enabled && enableLocalCheckbox ? View.VISIBLE
				: View.GONE;
		findViewById(R.id.sourceAccountSpinner).setVisibility(visState);
		findViewById(R.id.sourceAccountTitle).setVisibility(visState);
		findViewById(R.id.deleteLocalCheckbox)
				.setVisibility(localCheckboxState);
	}

	private void setNoContactsMsg(boolean enabled) {
		int visState = enabled ? View.VISIBLE : View.INVISIBLE;
		findViewById(R.id.noContactsMessage).setVisibility(visState);
		findViewById(R.id.startContactApp).setVisibility(visState);
		findViewById(R.id.startContactApp).setVisibility(visState);
	}

	private String gestDestinationAccountName() {
		String destAccountName = accountName;
		if (destAccountName == null) {
			Spinner destAcSpinner = (Spinner) findViewById(R.id.destAccountSpinner);
			Object item = destAcSpinner.getSelectedItem();
			destAccountName = item != null ? item.toString() : null;
		}
		return destAccountName;
	}

	/**
	 * Handles Import-Action
	 * 
	 * @param view
	 */
	public void handleImport(View view) {
		String destAccountName = gestDestinationAccountName();

		CheckBox delCheckbox = (CheckBox) findViewById(R.id.deleteLocalCheckbox);
		boolean deleteLocalContacts = delCheckbox.getVisibility() == View.VISIBLE
				&& delCheckbox.isChecked();

		Spinner sourceSpinner = (Spinner) findViewById(R.id.sourceAccountSpinner);
		AccountInfo acInfo = (AccountInfo) sourceSpinner.getSelectedItem();
		ImportProgressDialog dlg = ImportProgressDialog.newInstance(acInfo,
				destAccountName, deleteLocalContacts);
		dlg.show(this.getSupportFragmentManager(), "ImportProgressDialog");
	}

	private void updateBtnState() {
		Spinner sourceAcSpinner = (Spinner) findViewById(R.id.sourceAccountSpinner);
		AccountInfo sourceAcInfo = (AccountInfo) sourceAcSpinner
				.getSelectedItem();

		String destAccountName = gestDestinationAccountName();
		boolean destMatchSource = sourceAcInfo != null
				&& Constants.ACCOUNT_TYPE.equals(sourceAcInfo.accountType)
				&& TextUtils.equals(sourceAcInfo.accountName, destAccountName);

		boolean importEnabled = accountAvailable && sourceAcInfo != null
				&& !destMatchSource && destAccountName != null;

		// Enabled/Disable Import
		findViewById(R.id.buttonImport).setEnabled(importEnabled);

		boolean showSourceAccountSpinner = false;
		boolean showDestAccountSpinner = false;
		boolean showNoContactsMsg = false;

		if (sourceAccountAdapter.getCount() == 0) {
			showNoContactsMsg = true;
		} else {
			showSourceAccountSpinner = true;
			if (destAccountAdapter.getCount() > 0) {
				showDestAccountSpinner = true;
			}
		}

		setNoContactsMsg(showNoContactsMsg);
		setDestinationAccountSelector(showDestAccountSpinner);
		setSourceAccountSelector(showSourceAccountSpinner, sourceAcInfo != null
				&& sourceAcInfo.localAccount);
	}

	private void initStartContactBtn() {
		Button startBtn = (Button) findViewById(R.id.startContactApp);
		PackageManager packageManager = getPackageManager();
		Intent contactAppIntent = ContactManager.createContactAppIntent();
		boolean isIntentSafe = false;
		if (contactAppIntent != null) {
			List<ResolveInfo> activities = packageManager
					.queryIntentActivities(contactAppIntent, 0);
			isIntentSafe = !activities.isEmpty();

			Drawable icon = ContactManager.getContactAppIcon(this,
					contactAppIntent);
			startBtn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null,
					null);
		}
		startBtn.setEnabled(isIntentSafe);
	}

	public void handleStartPeople(View view) {
		Intent intent = ContactManager.createContactAppIntent();
		if (intent != null) {
			this.startActivity(intent);
		}
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		updateBtnState();
	}

	public void onNothingSelected(AdapterView<?> parent) {
		updateBtnState();
	}

	public void onCreateEnd(boolean success, boolean cancel, ImportError error,
			int importedCount, int availableEnd) {
		if (success) {
			finish();
		} else {
			TextView textView = (TextView) findViewById(R.id.message_bottom);
			if (!cancel && error != null) {
				int msgId;
				switch (error) {
				case DB_ERROR:
					msgId = R.string.import_activity_dberror;
					break;
				default:
					msgId = R.string.import_activity_failed;
					break;
				}

				textView.setVisibility(View.VISIBLE);
				textView.setText(getText(msgId));
			} else {
				textView.setVisibility(View.GONE);
			}

			if (textView.getVisibility() == View.VISIBLE) {
				textView.setError(getText(R.string.import_activity_retry));
			} else {
				textView.setError(null);
			}
		}
	}

	public Loader<List<AccountInfo>> onCreateLoader(int id, Bundle args) {
		return new AccountLoader(this);
	}

	public void onLoadFinished(Loader<List<AccountInfo>> loader,
			List<AccountInfo> data) {
		int bigestAccountIndex = -1;
		int currCount = -1;

		if (sourceAccountAdapter != null) {
			List<AccountInfo> myAccountsData = new ArrayList<AccountInfo>();
			List<AccountInfo> newListData = new ArrayList<AccountInfo>();
			int listPos = 0;
			for (AccountInfo info : data) {
				if (Constants.ACCOUNT_TYPE.equals(info.accountType)) {
					myAccountsData.add(info);
					continue;
				}
				if (info.getContactCount() > currCount) {
					currCount = info.getContactCount();
					bigestAccountIndex = listPos;
				}

				newListData.add(info);
				listPos++;
			}
			// If more than one of our accounts is here allow importing to
			// the other
			if (!myAccountsData.isEmpty()) {
				AccountManager accountManager = AccountManager
						.get(ImportActivity.this);
				Account[] myAccounts = accountManager
						.getAccountsByType(Constants.ACCOUNT_TYPE);
				if (myAccounts.length > 1) {
					for (AccountInfo accountInfo : myAccountsData) {
						if (accountInfo.getContactCount() > currCount) {
							currCount = accountInfo.getContactCount();
							bigestAccountIndex = listPos;
						}
						newListData.add(accountInfo);
						listPos++;
					}
				}
			}

			boolean containsOldData = sourceAccountAdapter.getCount() > 0;
			sourceAccountAdapter.setData(newListData);

			if (bigestAccountIndex >= 0 && !containsOldData) {
				Spinner spinner = (Spinner) findViewById(R.id.sourceAccountSpinner);
				if (spinner != null) {
					spinner.setSelection(bigestAccountIndex);
				}
			}
			updateBtnState();
		}
	}

	public void onLoaderReset(Loader<List<AccountInfo>> loader) {
		// ignore reset
	}

	/**
	 * Loader for available Accounts-Infos
	 * 
	 */
	private static class AccountLoader extends
			AbstractAsyncTaskLoader<List<AccountInfo>> {

		public AccountLoader(Context context) {
			super(context);
		}

		@Override
		public List<AccountInfo> loadInBackground() {
			return ContactManager.getContactAccounts(this.getContext());
		}
	}
}
