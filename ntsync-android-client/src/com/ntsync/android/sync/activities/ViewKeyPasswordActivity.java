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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ntsync.android.sync.BuildConfig;
import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.ClientKeyHelper;
import com.ntsync.android.sync.shared.Constants;

/**
 * Activity which displays key password input fields
 */
public class ViewKeyPasswordActivity extends AbstractFragmentActivity {

	private static final int PWD_WORD_LEN = 5;

	public ViewKeyPasswordActivity() {
		super(R.id.action_viewkey);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.viewkeypasswort_parent);
	}

	@Override
	protected void onResumeFragments() {
		super.onResumeFragments();
		refreshAccountList();
	}

	private void refreshAccountList() {
		FragmentManager mgr = getSupportFragmentManager();
		AccountManager accountManager = AccountManager.get(this);
		Account[] accounts = accountManager
				.getAccountsByType(Constants.ACCOUNT_TYPE);
		FragmentTransaction trans = mgr.beginTransaction();

		boolean isFirst = true;
		for (Account currAccount : accounts) {
			AccountFragment fragment = new AccountFragment();
			Bundle args = new Bundle();
			args.putString(AccountFragment.PARAM_ACCOUNT_NAME, currAccount.name);
			fragment.setArguments(args);

			if (isFirst) {
				trans.replace(R.id.accountContainer, fragment);
				isFirst = false;
			} else {
				trans.add(R.id.accountContainer, fragment);
			}
		}
		trans.commit();
	}

	public static class AccountFragment extends Fragment {

		public static final String PARAM_ACCOUNT_NAME = "name";

		private static final String TAG = "AccountFragment";

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			// Inflate the layout for this fragment
			View view = inflater.inflate(R.layout.viewkeypassword_activity,
					container, false);

			String accountName = getArguments().getString(PARAM_ACCOUNT_NAME);
			AccountManager accountManager = AccountManager.get(this
					.getActivity());
			String pwd = ClientKeyHelper.getKeyPwd(new Account(accountName,
					Constants.ACCOUNT_TYPE), accountManager);

			((TextView) view.findViewById(R.id.accountTitle))
					.setText(accountName);

			int msgBottom = R.string.empty;
			if (pwd != null && pwd.length() > 0) {
				String[] words = pwd.split(" ");
				if (words.length == PWD_WORD_LEN) {
					TextView[] mPasswordEdit = new TextView[PWD_WORD_LEN];
					mPasswordEdit[0] = (TextView) view
							.findViewById(R.id.pwd1_edit);
					mPasswordEdit[1] = (TextView) view
							.findViewById(R.id.pwd2_edit);
					mPasswordEdit[2] = (TextView) view
							.findViewById(R.id.pwd3_edit);
					mPasswordEdit[3] = (TextView) view
							.findViewById(R.id.pwd4_edit);
					mPasswordEdit[4] = (TextView) view
							.findViewById(R.id.pwd5_edit);

					for (int i = 0; i < words.length; i++) {
						mPasswordEdit[i].setText(words[i]);
					}
				} else {
					Log.e(TAG, "Wrong password format. Words-Length:"
							+ words.length);
					if (BuildConfig.DEBUG) {
						Log.e(TAG, "Password:" + pwd);
					}
				}
				view.findViewById(R.id.message).setVisibility(View.VISIBLE);
				view.findViewById(R.id.pwd_group).setVisibility(View.VISIBLE);
			} else {
				view.findViewById(R.id.pwd_group).setVisibility(View.GONE);
				view.findViewById(R.id.message).setVisibility(View.GONE);

				msgBottom = R.string.viewkeypwd_activity_noppwd;
			}

			TextView msgView = (TextView) view
					.findViewById(R.id.message_bottom);
			msgView.setText(msgBottom);
			return view;
		}
	}
}
