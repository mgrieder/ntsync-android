package com.ntsync.android.sync.activities;

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.List;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.AccountInfo;

/**
 * Adapter for showing a List of Contact Accounts
 */
public class AccountAdapter extends BaseAdapter {

	private LayoutInflater mInflater;
	private int textViewResourceId;
	private List<AccountInfo> items;

	public AccountAdapter(Context context, int textViewResourceId,
			List<AccountInfo> items) {
		super();
		this.textViewResourceId = textViewResourceId;
		this.items = items;
		mInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View view;

		if (convertView == null) {
			view = mInflater.inflate(textViewResourceId, parent, false);
		} else {
			view = convertView;
		}

		TextView textMain = (TextView) view.findViewById(R.id.textMain);
		TextView textSubitem = (TextView) view.findViewById(R.id.textSubitem);
		float smallTextSize = textMain.getTextSize() * 0.8f;
		textSubitem.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize);

		TextView textInfo = (TextView) view.findViewById(R.id.textInfo);
		textInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize);

		AccountInfo item = items.get(position);
		textMain.setText(item.displayName);
		textSubitem.setText(item.isHideName() ? "" : item.accountName);
		textInfo.setText(String.valueOf(item.getContactCount()));

		return view;
	}

	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent);
	}

	public int getCount() {
		return items.size();
	}

	public Object getItem(int position) {
		return items.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	public void add(AccountInfo item) {
		items.add(item);
		notifyDataSetChanged();
	}

	public void setData(List<AccountInfo> newListData) {
		// Check if data has changed
		if (!newListData.equals(items)) {
			items.clear();
			items.addAll(newListData);
			notifyDataSetChanged();
		}
	}
}
