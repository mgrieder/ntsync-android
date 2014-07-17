package com.ntsync.android.sync.client;

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

import java.util.concurrent.Semaphore;

import javax.crypto.SecretKey;

import junit.framework.Assert;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import com.ntsync.android.sync.shared.Constants;

public class ClientKeyHelperTest extends InstrumentationTestCase {

	private RemoveListener removeListener;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		this.removeListener = new RemoveListener();
	}

	@SmallTest
	public void testConstructor() throws Exception {
		Account account = new Account("user1", Constants.ACCOUNT_TYPE);
		AccountManager accountManager = AccountManager.get(this
				.getInstrumentation().getTargetContext());
		// Remove && Add Account other account info could not be saved
		accountManager.removeAccount(account, removeListener, null);
		// Waiting until Account is removed
		removeListener.sem.acquire();
		boolean added = accountManager
				.addAccountExplicitly(account, null, null);
		Assert.assertTrue(added);

		SecretKey key = ClientKeyHelper.getOrCreatePrivateKey(account,
				accountManager);
		Assert.assertNotNull(key);

		String pwd = ClientKeyHelper.getKeyPwd(account, accountManager);
		String salt = ClientKeyHelper.getSalt(account, accountManager);
		String pwCheck = ClientKeyHelper.getPwdCheck(account, accountManager);

		Assert.assertNotNull(pwd);
		Assert.assertNotNull(salt);
		Assert.assertNotNull(pwCheck);

		// Recrete with the same information should generate the same key
		SecretKey key2 = ClientKeyHelper.createKey(account, accountManager,
				pwd, Base64.decode(salt, Base64.DEFAULT), true,
				Base64.decode(pwCheck, Base64.DEFAULT));
		Assert.assertEquals(key, key2);

		accountManager.removeAccount(account, removeListener, null);
		removeListener.sem.acquire();
	}

	private static class RemoveListener implements
			AccountManagerCallback<Boolean> {

		public final Semaphore sem = new Semaphore(0);

		public void run(AccountManagerFuture<Boolean> future) {
			sem.release();
		}

	}
}
