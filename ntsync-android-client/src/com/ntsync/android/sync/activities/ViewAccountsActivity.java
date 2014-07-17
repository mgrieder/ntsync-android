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

import java.text.SimpleDateFormat;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.shared.AccountStatistic;
import com.ntsync.shared.Restrictions;

/**
 * Displays all our accounts and their state
 */
public class ViewAccountsActivity extends AbstractFragmentActivity implements
		LoaderCallbacks<List<AccountStatistic>> {

	private AccountStatisticListAdapter adapter;

	private static final int LOADERID_ACCOUNTLIST = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account_statistic);

		getListView().setEmptyView(findViewById(R.id.progressBar));
		adapter = new AccountStatisticListAdapter(this);
		getListView().setAdapter(adapter);
	}

	@Override
	protected void onResumeFragments() {
		getSupportLoaderManager().initLoader(LOADERID_ACCOUNTLIST, null, this);
	}

	private ListView getListView() {
		return (ListView) findViewById(R.id.accountItemList);
	}

	public Loader<List<AccountStatistic>> onCreateLoader(int id, Bundle args) {
		return new AccountStatisticListLoader(this);

	}

	public void onLoadFinished(Loader<List<AccountStatistic>> loader,
			List<AccountStatistic> data) {
		adapter.setData(data);
	}

	public void onLoaderReset(Loader<List<AccountStatistic>> loader) {
		adapter.setData(null);
	}

	/**
	 * Handles Button-Click
	 * 
	 * @param view
	 */
	public void handleBuy(View view) {
		// Find the account for clicked Row
		int position = getListView()
				.getPositionForView((View) view.getParent());
		AccountStatistic accountStat = (AccountStatistic) getListView()
				.getItemAtPosition(position);
		if (accountStat != null) {
			Intent intent = new Intent(this, ShopActivity.class);
			intent.putExtra(ShopActivity.PARM_ACCOUNT_NAME,
					accountStat.accountName);
			startActivity(intent);
		}
	}

	public static class AccountStatisticListAdapter extends
			ArrayAdapter<AccountStatistic> {
		private static final String UNKNOWN_STR = "?";
		private final LayoutInflater mInflater;

		public AccountStatisticListAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_2);
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public void setData(List<AccountStatistic> dataList) {
			clear();
			if (dataList != null) {
				// addAll need Api 11: use workaround
				setNotifyOnChange(false);
				for (AccountStatistic data : dataList) {
					add(data);
				}
				setNotifyOnChange(true);
				notifyDataSetChanged();
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;

			if (convertView == null) {
				view = mInflater.inflate(
						R.layout.accountstatistic_listitem_row, parent, false);
			} else {
				view = convertView;
			}

			AccountStatistic item = getItem(position);
			Restrictions restr = item.restrictions;

			((TextView) view.findViewById(R.id.name)).setText(item.accountName);

			Resources res = view.getResources();

			String contactString = String.format(
					res.getText(R.string.accountstatistic_activity_contacts)
							.toString(), item.contactCount,
					restr != null ? restr.getMaxContactCount() : UNKNOWN_STR);
			((TextView) view.findViewById(R.id.contactCount))
					.setText(contactString);

			String groupString = String.format(
					res.getText(R.string.accountstatistic_activity_groups)
							.toString(), item.contactGroupCount,
					restr != null ? restr.getMaxGroupCount() : UNKNOWN_STR);
			((TextView) view.findViewById(R.id.groupCount))
					.setText(groupString);

			int enableStrId = restr != null && restr.isPhotoSyncSupported() ? R.string.accountstatistic_activity_activ
					: R.string.accountstatistic_activity_inactiv;
			String photoString = String
					.format(res.getText(
							R.string.accountstatistic_activity_photosupport)
							.toString(), res.getText(enableStrId));
			((TextView) view.findViewById(R.id.photoSupport))
					.setText(photoString);

			CharSequence validString;
			if (restr != null && restr.getValidUntil() != null) {
				String untilStr = SimpleDateFormat.getDateInstance().format(
						restr.getValidUntil());
				validString = String.format(
						res.getText(
								R.string.accountstatistic_activity_validuntil)
								.toString(), untilStr);
			} else {
				validString = restr != null ? res
						.getText(R.string.accountstatistic_activity_nosubscription)
						: "";
			}
			((TextView) view.findViewById(R.id.validUntil))
					.setText(validString);

			return view;
		}
	}
}
