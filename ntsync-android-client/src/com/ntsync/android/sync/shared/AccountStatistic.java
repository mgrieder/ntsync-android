package com.ntsync.android.sync.shared;

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

import java.util.Date;

import com.ntsync.shared.Restrictions;

/**
 * Provides Metadata about one of our Sync-Accounts.
 * */
public class AccountStatistic {

	public final String accountName;

	public final int contactCount;

	public final int contactGroupCount;

	public final Restrictions restrictions;

	public final AccountSyncResult syncResult;

	public final Date nextSync;

	public final boolean autoSync;

	public AccountStatistic(String accountName, int contactCount,
			int contactGroupCount, Restrictions restrictions, AccountSyncResult syncResult,
			Date nextSync, boolean autoSync) {
		super();
		this.accountName = accountName;
		this.contactCount = contactCount;
		this.contactGroupCount = contactGroupCount;
		this.restrictions = restrictions;
		this.syncResult = syncResult;
		this.nextSync = nextSync;
		this.autoSync = autoSync;
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
		AccountStatistic other = (AccountStatistic) obj;
		if (accountName == null) {
			if (other.accountName != null) {
				return false;
			}
		} else if (!accountName.equals(other.accountName)) {
			return false;
		}
		if (contactCount != other.contactCount) {
			return false;
		}
		if (contactGroupCount != other.contactGroupCount) {
			return false;
		}
		if (restrictions == null) {
			if (other.restrictions != null) {
				return false;
			}
		} else if (!restrictions.equals(other.restrictions)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return accountName != null ? accountName.hashCode() : 0;
	}
}