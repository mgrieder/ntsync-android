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

/**
 * Provides a Handler for Restriction Conflicts when some data will not be
 * synchronized because of Restrictions.
 */
public interface RestrictionConflictHandler {

	/**
	 * Called when a RawContact has a Photo but Restrictions don't allow the
	 * synchronization of Photos
	 * 
	 * @param rawContactId
	 */
	void onPhotoNotSynced(long rawContactId, long dataId);

	/**
	 * Called when a RawContact will not be synchronized because Restrictions
	 * doesn't allow more Contacts.
	 * 
	 * @param rawContactId
	 * @param totalLocalContacts
	 * @param maxCount
	 */
	void onContactNotSynced(long rawContactId, int maxCount,
			int totalLocalContacts);
}
