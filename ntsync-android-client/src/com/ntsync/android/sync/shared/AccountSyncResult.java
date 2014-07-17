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

/**
 * Provides access to the Result of the last Sync for an account.
 */
public class AccountSyncResult {

	private String accountName;

	private Date lastSyncTime;

	private String errorMsg;

	private SyncResultState state;

	public AccountSyncResult() {
		this(null);
	}

	public AccountSyncResult(String accountName) {
		this.accountName = accountName;
		this.state = SyncResultState.FAILED;
	}

	public Date getLastSyncTime() {
		return lastSyncTime;
	}

	public void setLastSyncTime(Date lastSyncTime) {
		this.lastSyncTime = lastSyncTime;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public SyncResultState getState() {
		return state;
	}

	/**
	 * Set new State. Default is {@link SyncResultState#FAILED}
	 * 
	 * @param state
	 */
	public void setState(SyncResultState state) {
		this.state = state;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}
}
