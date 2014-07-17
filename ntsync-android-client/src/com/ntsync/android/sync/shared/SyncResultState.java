package com.ntsync.android.sync.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

public enum SyncResultState {
	SUCCESS, NETWORK_ERROR, FAILED, AUTH_FAILED,
	/** Missing Key for en/decryption */
	MISSING_KEY,

	/** Temporary Server-Error */
	SERVER_ERROR;

	private static final Map<String, SyncResultState> NAMES_MAP = new HashMap<String, SyncResultState>(
			3);

	static {
		NAMES_MAP.put("success", SUCCESS);
		NAMES_MAP.put("network-error", NETWORK_ERROR);
		NAMES_MAP.put("auth_failed", AUTH_FAILED);
		NAMES_MAP.put("failed", FAILED);
		NAMES_MAP.put("missing-key", MISSING_KEY);
		NAMES_MAP.put("server-error", SERVER_ERROR);
	}

	@JsonCreator
	public static SyncResultState forValue(String value) {
		return NAMES_MAP.get(value);
	}

	@JsonValue
	public String toValue() {
		for (Entry<String, SyncResultState> entry : NAMES_MAP.entrySet()) {
			if (entry.getValue() == this) {
				return entry.getKey();
			}
		}
		return null; // or fail
	}
}