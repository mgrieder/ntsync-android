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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Statistic Info about an Account for Display
 */
public class AccountInfo implements Parcelable {
	public final String displayName;

	public final String accountType;

	public final String accountName;

	public final boolean localAccount;

	private int contacts = 0;

	private boolean hideName = false;

	private AccountInfo(Parcel in) {
		displayName = in.readString();
		accountType = in.readString();
		accountName = in.readString();
		contacts = in.readInt();
		localAccount = Boolean.parseBoolean(in.readString());
	}

	public AccountInfo(String displayName, String accountType,
			String accountName, boolean localAccount) {
		this.displayName = displayName;
		this.accountType = accountType;
		this.accountName = accountName;
		this.localAccount = localAccount;

	}

	public int getContactCount() {
		return contacts;
	}

	public void incContactCount() {
		contacts++;
	}

	public void setContactCount(int count) {
		contacts = count;
	}

	public boolean isLocalAccount() {
		return localAccount;
	}

	public boolean isHideName() {
		return hideName;
	}

	public void setHideName(boolean hideName) {
		this.hideName = hideName;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(displayName);
		out.writeString(accountType);
		out.writeString(accountName);
		out.writeInt(contacts);
		out.writeString(Boolean.toString(localAccount));
	}

	public static final Parcelable.Creator<AccountInfo> CREATOR = new Parcelable.Creator<AccountInfo>() {
		public AccountInfo createFromParcel(Parcel in) {
			return new AccountInfo(in);
		}

		public AccountInfo[] newArray(int size) {
			return new AccountInfo[size];
		}
	};

	@Override
	public int hashCode() {
		return accountName != null ? accountName.hashCode() : 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AccountInfo other = (AccountInfo) obj;
		if (accountName == null) {
			if (other.accountName != null) {
				return false;
			}
		} else if (!accountName.equals(other.accountName)) {
			return false;
		}
		if (accountType == null) {
			if (other.accountType != null) {
				return false;
			}
		} else if (!accountType.equals(other.accountType)) {
			return false;
		}
		if (contacts != other.contacts) {
			return false;
		}
		if (displayName == null) {
			if (other.displayName != null) {
				return false;
			}
		} else if (!displayName.equals(other.displayName)) {
			return false;
		}
		if (hideName != other.hideName) {
			return false;
		}
		if (localAccount != other.localAccount) {
			return false;
		}
		return true;
	}
}