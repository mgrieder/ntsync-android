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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.shared.AccountStatistic;
import com.ntsync.android.sync.shared.AccountSyncResult;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.SyncResultState;
import com.ntsync.android.sync.shared.SyncUtils;

/**
 * Shows all our Sync-Accounts with their sync-state
 */
public class AccountListFragment extends ListFragment implements
		LoaderCallbacks<List<AccountStatistic>>, SyncStatusObserver {

	private static final int LOADERID_ACCOUNTLIST = 1;

	private static final int REGISTRATION_CODE = 2;

	private ArrayAdapter<AccountStatistic> listAdapter;

	private Object statusListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		listAdapter = new AppListAdapter(this.getActivity());
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		Context context = view.getContext();
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setLayoutParams(new LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);

		Button loginBtn = new Button(context);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.rightMargin = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 8, view.getResources()
						.getDisplayMetrics());
		loginBtn.setLayoutParams(layoutParams);
		loginBtn.setText(context.getResources().getText(
				R.string.accountlist_login_button));
		loginBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startLoginView();
			}
		});

		Button createAccountBtn = new Button(context);
		createAccountBtn.setLayoutParams(new LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		createAccountBtn.setText(context.getResources().getText(
				R.string.accountlist_creataccount_button));
		createAccountBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				createAccountView();
			}
		});

		linearLayout.addView(loginBtn);
		linearLayout.addView(createAccountBtn);
		((ViewGroup) this.getListView().getParent()).addView(linearLayout);
		this.getListView().setEmptyView(linearLayout);
	}

	void startLoginView() {
		Intent intent = new Intent(this.getActivity(),
				AuthenticatorActivity.class);
		startActivity(intent);
	}

	void createAccountView() {
		Intent intent = new Intent(this.getActivity(), RegisterActivity.class);
		startActivityForResult(intent, REGISTRATION_CODE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REGISTRATION_CODE
				&& resultCode == Activity.RESULT_OK) {
			// Create Account for new Registration
			String username = data
					.getStringExtra(RegisterActivity.PARAM_USERNAME);
			String password = data.getStringExtra(RegisterActivity.PARAM_PWD);
			Account account = new Account(username, Constants.ACCOUNT_TYPE);
			Context context = this.getActivity();
			AccountManager acm = AccountManager.get(context);
			SyncUtils.createAccount(context, account, acm, password);
		}
	}

	public Loader<List<AccountStatistic>> onCreateLoader(int id, Bundle args) {
		return new AccountStatisticListLoader(this.getActivity());
	}

	public void onLoadFinished(Loader<List<AccountStatistic>> loader,
			List<AccountStatistic> dataList) {
		listAdapter.clear();
		if (dataList != null) {
			// addAll need Api 11: use workaround
			listAdapter.setNotifyOnChange(false);
			for (AccountStatistic data : dataList) {
				listAdapter.add(data);
			}
			listAdapter.setNotifyOnChange(true);
			listAdapter.notifyDataSetChanged();
		}
		this.setListAdapter(listAdapter);
	}

	public void onLoaderReset(Loader<List<AccountStatistic>> loader) {
		this.setListAdapter(null);
	}

	@Override
	public void onResume() {
		super.onResume();
		getLoaderManager().restartLoader(LOADERID_ACCOUNTLIST, null, this);

		statusListener = ContentResolver.addStatusChangeListener(
				ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
	}

	@Override
	public void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(statusListener);
	}

	public void onStatusChanged(int which) {
		// Refresh View
		this.getListView().post(new Runnable() {
			public void run() {
				if (!AccountListFragment.this.isDetached()
						&& listAdapter != null) {
					AccountListFragment.this.getLoaderManager().restartLoader(
							LOADERID_ACCOUNTLIST, null,
							AccountListFragment.this);
				}
			}
		});
	}

	public static class AppListAdapter extends ArrayAdapter<AccountStatistic> {
		private final LayoutInflater mInflater;

		private DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(
				DateFormat.SHORT, DateFormat.SHORT);
		private DateFormat timeFormat = DateFormat
				.getTimeInstance(DateFormat.SHORT);

		private final Drawable failedRefreshImg;

		private final Drawable okRefreshImg;

		public AppListAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_2);
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			failedRefreshImg = context.getResources().getDrawable(
					R.drawable.refresh_failed);
			okRefreshImg = context.getResources().getDrawable(
					R.drawable.refresh_ok);
		}

		public void setData(List<AccountStatistic> data) {
			clear();
			if (data != null) {
				// addAll need Api 11: use workaround
				setNotifyOnChange(false);
				for (AccountStatistic item : data) {
					add(item);
				}
				setNotifyOnChange(true);
				notifyDataSetChanged();
			}
		}

		protected void startSync(String accountName) {
			if (SyncUtils.isSyncActive(accountName)) {
				return;
			}
			if (!SyncUtils.isNetworkConnected(this.getContext())) {
				Toast.makeText(getContext(), R.string.no_network,
						Toast.LENGTH_SHORT).show();
				return;
			}
			Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
			Bundle args = new Bundle();
			args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			ContentResolver.requestSync(account, Constants.CONTACT_AUTHORITY,
					args);

		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;

			if (convertView == null) {
				view = mInflater.inflate(R.layout.contact_account_item, parent,
						false);
			} else {
				view = convertView;
			}

			final AccountStatistic item = getItem(position);
			final String accountName = item.accountName;

			Resources res = view.getResources();

			Calendar cal = new GregorianCalendar();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long startTimeToday = cal.getTimeInMillis();

			final AccountSyncResult syncResult = item.syncResult;
			Date lastSync = syncResult != null ? syncResult.getLastSyncTime()
					: null;
			DateFormat format = lastSync != null
					&& lastSync.getTime() < startTimeToday ? dateTimeFormat
					: timeFormat;
			CharSequence timeLastText = lastSync != null ? format
					.format(lastSync) : res.getText(R.string.not_available);
			CharSequence lastSyncText = String.format(
					res.getText(R.string.accountlist_lastsync).toString(),
					timeLastText);
			cal.add(Calendar.DAY_OF_MONTH, 1);
			long startTimeNexDay = cal.getTimeInMillis();
			format = item.nextSync != null
					&& item.nextSync.getTime() >= startTimeNexDay ? dateTimeFormat
					: timeFormat;
			CharSequence timeNextText = item.nextSync != null ? format
					.format(item.nextSync) : res
					.getText(R.string.not_available);

			CharSequence nextSyncText = item.nextSync != null ? String.format(
					res.getText(R.string.accountlist_nextsync).toString(),
					timeNextText) : res
					.getText(R.string.accountlist_autosync_disabled);

			((TextView) view.findViewById(R.id.textAccountName))
					.setText(item.accountName);
			((TextView) view.findViewById(R.id.textContactCount))
					.setText(String.valueOf(item.contactCount));

			TextView lastSyncView = (TextView) view
					.findViewById(R.id.textLastSync);
			lastSyncView.setText(lastSyncText);
			TextView nextSyncView = (TextView) view
					.findViewById(R.id.textNextSync);
			nextSyncView.setText(nextSyncText);

			// Set Green/Orange Imaged based on last success/error
			ImageView syncStateImg = (ImageView) view
					.findViewById(R.id.syncImg);
			if (syncResult != null
					&& syncResult.getState() == SyncResultState.SUCCESS) {
				syncStateImg.setImageDrawable(okRefreshImg);
			} else {
				syncStateImg.setImageDrawable(failedRefreshImg);
			}

			OnClickListener errorViewListener = null;

			OnClickListener startSyncListener = new OnClickListener() {
				public void onClick(View v) {
					// Start Sync if not already running
					startSync(accountName);
				}
			};

			syncStateImg.setOnClickListener(startSyncListener);

			final String errorMsg = syncResult != null ? syncResult
					.getErrorMsg() : null;
			TextView errorMsgView = (TextView) view
					.findViewById(R.id.textErrorMsg);

			if (errorMsg != null) {
				errorMsgView.setText(errorMsg);
				errorViewListener = new OnClickListener() {
					public void onClick(View v) {
						MessageDialog.show(errorMsg,
								((FragmentActivity) v.getContext()));
					}
				};
			}

			OnClickListener nextSyncListener = errorViewListener;
			if (item.nextSync == null) {
				nextSyncListener = startSyncListener;
				lastSyncView.setOnClickListener(nextSyncListener);
			} else {
				lastSyncView.setOnClickListener(null);
			}
			errorMsgView.setOnClickListener(errorViewListener);
			nextSyncView.setOnClickListener(startSyncListener);

			errorMsgView.setVisibility(errorMsg != null ? View.VISIBLE
					: View.INVISIBLE);

			if (SyncUtils.isSyncActive(accountName)) {
				RotateAnimation anim = new RotateAnimation(0f, 360f,
						Animation.RELATIVE_TO_SELF, 0.5f,
						Animation.RELATIVE_TO_SELF, 0.5f);
				anim.setRepeatCount(Animation.INFINITE);
				anim.setFillAfter(true);
				anim.setInterpolator(new LinearInterpolator());
				anim.setDuration(3000);

				syncStateImg.startAnimation(anim);
			} else {
				syncStateImg.setAnimation(null);
			}

			return view;
		}
	}
}
