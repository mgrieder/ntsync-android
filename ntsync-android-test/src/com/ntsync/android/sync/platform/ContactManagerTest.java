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

import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.TestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.ntsync.shared.ContactConstants.AddressType;
import com.ntsync.shared.ContactConstants.EmailType;
import com.ntsync.shared.ContactConstants.EventType;
import com.ntsync.shared.ContactConstants.ImProtocolType;
import com.ntsync.shared.ContactConstants.ImType;
import com.ntsync.shared.ContactConstants.NicknameType;
import com.ntsync.shared.ContactConstants.OrganizationType;
import com.ntsync.shared.ContactConstants.PhoneType;
import com.ntsync.shared.ContactConstants.RelationType;

public class ContactManagerTest extends TestCase {

	@SmallTest
	public void testGetAndroidPhoneType() {
		PhoneType[] types = PhoneType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (PhoneType phoneType : types) {
			int type = ContactManager.getAndroidPhoneType(phoneType);
			Assert.assertFalse(hashSet.contains(type));
			hashSet.add(type);

			PhoneType newPhoneType = ContactManager.getPhoneType(type);
			Assert.assertEquals(phoneType, newPhoneType);
		}
	}

	@SmallTest
	public void testGetAndroidEmailType() {
		EmailType[] types = EmailType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (EmailType emailType : types) {
			int type = ContactManager.getAndroidEmailType(emailType);
			Assert.assertFalse(hashSet.contains(type));
			hashSet.add(type);

			EmailType newEmailType = ContactManager.getEmailType(type);
			Assert.assertEquals(emailType, newEmailType);
		}
	}

	@SmallTest
	public void testGetAndroidEventsType() {
		EventType[] types = EventType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (EventType type : types) {
			int aType = ContactManager.getAndroidEventType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			EventType newType = ContactManager.getEventType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidRelationType() {
		RelationType[] types = RelationType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (RelationType type : types) {
			int aType = ContactManager.getAndroidRelationType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			RelationType newType = ContactManager.getRelationType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidNicknameType() {
		NicknameType[] types = NicknameType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (NicknameType type : types) {
			int aType = ContactManager.getAndroidNicknameType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			NicknameType newType = ContactManager.getNicknameType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidAddressesType() {
		AddressType[] types = AddressType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (AddressType type : types) {
			int aType = ContactManager.getAndroidAddressType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			AddressType newType = ContactManager.getAddressType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidImType() {
		ImType[] types = ImType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (ImType type : types) {
			int aType = ContactManager.getAndroidImType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			ImType newType = ContactManager.getImType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidImProtocolType() {
		ImProtocolType[] types = ImProtocolType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (ImProtocolType type : types) {
			int aType = ContactManager.getAndroidImProtocolType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			ImProtocolType newType = ContactManager.getImProtocolType(aType);
			Assert.assertEquals(type, newType);
		}
	}

	@SmallTest
	public void testGetAndroidOrganizationProtocolType() {
		OrganizationType[] types = OrganizationType.values();

		Set<Integer> hashSet = new HashSet<Integer>();
		for (OrganizationType type : types) {
			int aType = ContactManager.getAndroidOrganizationType(type);
			Assert.assertFalse(hashSet.contains(aType));
			hashSet.add(aType);

			OrganizationType newType = ContactManager
					.getOrganizationType(aType);
			Assert.assertEquals(type, newType);
		}
	}
}
