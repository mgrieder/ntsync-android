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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.auth.AuthenticationException;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.modes.AEADBlockCipher;
import org.spongycastle.crypto.params.AEADParameters;
import org.spongycastle.crypto.params.KeyParameter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.ntsync.android.sync.shared.Constants;
import com.ntsync.shared.CryptoHelper;
import com.ntsync.shared.KeyGenerator;
import com.ntsync.shared.Pair;
import com.ntsync.shared.PasswortGenerator;
import com.ntsync.shared.SyncDataHelper;

@SuppressLint("TrulyRandom")
public final class ClientKeyHelper {

	private static final int PWD_WORD_LEN = 5;
	private static final int UPC_MULTIPLIER = 3;
	private static final int UPC_MODVAL = 10;
	private static final int UPC_NR_LEN = 11;

	private ClientKeyHelper() {

	}

	private static final String PRIVATE_KEY = "com.myllih.android.sync.key";
	private static final String PRIVATE_PWD = "com.myllih.android.sync.keypwd";
	private static final String PRIVATE_KEYSALT = "com.myllih.android.sync.keysalt";
	private static final String PRIVATE_PWDCHECK = "com.myllih.android.sync.pwdcheck";
	private static final String PRIVATE_KEY_SALTSAVED = "com.myllih.android.sync.keysaltsaved";

	private static final String TAG = "ClientKeyHelper";

	/** Length of Salt for Key in byte (current: 64 bits) */
	public static final int SALT_LENGHT = 8;

	private static final int IV_LENGTH = CryptoHelper.IV_LEN;

	/**
	 * 
	 * Get Private Key or return null.
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return Private Key
	 */
	public static SecretKey getPrivateKey(Account account,
			AccountManager accountManager) {
		// TODO: Redesign more secure location to safe private key?
		String keyString = accountManager.getUserData(account, PRIVATE_KEY);
		SecretKey key = null;
		if (!TextUtils.isEmpty(keyString)) {
			key = new SecretKeySpec(Base64.decode(keyString, Base64.DEFAULT),
					"AES");
		}
		return key;
	}

	/**
	 * 
	 * Get Salt for current Private Key generation or return null.
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return Salt as Base64 Encoded
	 */
	public static String getSalt(Account account, AccountManager accountManager) {
		String saltString = accountManager
				.getUserData(account, PRIVATE_KEYSALT);
		if (TextUtils.isEmpty(saltString)) {
			saltString = null;
		}
		return saltString;
	}

	/**
	 * 
	 * Get Password-Check-Value or null
	 * 
	 * @param account
	 * @return Password-Check as Base64 Encoded or null
	 */
	public static String getPwdCheck(Account account,
			AccountManager accountManager) {
		String saltString = accountManager.getUserData(account,
				PRIVATE_PWDCHECK);
		if (TextUtils.isEmpty(saltString)) {
			saltString = null;
		}
		return saltString;
	}

	/**
	 * Separate the Salt and PwdCheck-Values in separate byte-Arrays
	 * 
	 * @param saltPwdCheck
	 *            null is not allowed.
	 * @return salt and PwdCheck never null
	 */
	public static Pair<byte[], byte[]> splitSaltPwdCheck(byte[] saltPwdCheck) {
		byte[] salt = new byte[ClientKeyHelper.SALT_LENGHT];
		System.arraycopy(saltPwdCheck, 0, salt, 0, salt.length);
		byte[] pwdCheck = new byte[saltPwdCheck.length - salt.length];
		System.arraycopy(saltPwdCheck, salt.length, pwdCheck, 0,
				pwdCheck.length);
		return new Pair<byte[], byte[]>(salt, pwdCheck);
	}

	/**
	 * 
	 * Get Private Key or create a new one.
	 * 
	 * @param account
	 *            the account we're syncing
	 * @return Private Key
	 * @throws InvalidKeyException
	 */
	@SuppressLint("TrulyRandom")
	public static SecretKey getOrCreatePrivateKey(Account account,
			AccountManager accountManager) throws IOException,
			InvalidKeyException {
		SecretKey key = getPrivateKey(account, accountManager);
		if (key == null) {
			Log.i(TAG, "Create new private Key");

			String pwd = PasswortGenerator.createPwd(PWD_WORD_LEN);

			SecureRandom random = new SecureRandom();
			byte[] salt = new byte[SALT_LENGHT];
			random.nextBytes(salt);

			key = createKey(account, accountManager, pwd, salt, false, null);
		}
		return key;
	}

	/**
	 * 
	 * @param account
	 * @param accountManager
	 * @return true if Salt was saved on the server
	 */
	public static boolean isSaltSaved(Account account,
			AccountManager accountManager) {
		return Boolean.valueOf(accountManager.getUserData(account,
				PRIVATE_KEY_SALTSAVED));
	}

	/**
	 * Set Salt saved to true
	 * 
	 * @param account
	 * @param accountManager
	 */
	public static void setSaltSaved(Account account,
			AccountManager accountManager) {
		accountManager.setUserData(account, PRIVATE_KEY_SALTSAVED, "true");
	}

	/**
	 * 
	 * @param account
	 * @param accountManager
	 * @param keyPwd
	 *            Password for Key
	 * @param salt
	 * @param existingSalt
	 * @param pwdCheck
	 *            null for new Key otherwise used to Check if it is the right
	 *            Password.
	 * @return
	 * @throws InvalidKeyException
	 * @throws UnsupportedEncodingException
	 */
	public static SecretKey createKey(Account account,
			AccountManager accountManager, String keyPwd, byte[] salt,
			boolean existingSalt, byte[] pwdCheck) throws InvalidKeyException,
			UnsupportedEncodingException {

		KeyGenerator keyGen = new KeyGenerator();
		SecretKey skey = keyGen.generateKey(keyPwd, salt);

		byte[] raw = skey.getEncoded();
		String keyValue = Base64.encodeToString(raw, Base64.DEFAULT);
		String saltStr = Base64.encodeToString(salt, Base64.DEFAULT);

		assert (existingSalt ? pwdCheck != null : true);

		byte[] check = pwdCheck;
		if (existingSalt && pwdCheck != null) {
			// Validate new Passwort
			validateKey(check, skey);

		} else if (!existingSalt) {
			check = createPwdCheck(skey);
		}
		String pwdCheckStr = check != null ? Base64.encodeToString(check,
				Base64.DEFAULT) : null;

		accountManager.setUserData(account, PRIVATE_KEY_SALTSAVED,
				existingSalt ? "true" : "false");
		accountManager.setUserData(account, PRIVATE_KEYSALT, saltStr);
		accountManager.setUserData(account, PRIVATE_PWDCHECK, pwdCheckStr);
		accountManager.setUserData(account, PRIVATE_PWD, keyPwd);
		accountManager.setUserData(account, PRIVATE_KEY, keyValue);
		return skey;
	}

	private static void validateKey(byte[] check, SecretKey skey)
			throws InvalidKeyException {
		AEADBlockCipher cipher = CryptoHelper.getCipher();
		try {
			// data, pos, IV_LEN)
			byte[] iv = new byte[CryptoHelper.IV_LEN];
			System.arraycopy(check, 0, iv, 0, CryptoHelper.IV_LEN);
			cipher.init(false,
					new AEADParameters(new KeyParameter(skey.getEncoded()),
							CryptoHelper.MAC_SIZE, iv));
			byte[] original = CryptoHelper.cipherData(cipher, check, IV_LENGTH,
					check.length - IV_LENGTH);

			String orgValue = new String(original,
					SyncDataHelper.DEFAULT_CHARSET_NAME);
			// Validate Checksum
			int res1 = calcUpcChecksum(orgValue);
			int len = orgValue.length();
			if (orgValue.length() == 0
					|| res1 != Integer.parseInt(orgValue
							.substring(len - 1, len))) {
				throw new InvalidKeyException("Invalid Key. Checksum error");
			}
		} catch (NumberFormatException ex) {
			throw new InvalidKeyException("Invalid Key. Data is not number.",
					ex);
		} catch (DataLengthException e) {
			throw new InvalidKeyException("Invalid Key", e);
		} catch (IllegalStateException e) {
			throw new InvalidKeyException("Invalid Key. Wrong Parameter.", e);
		} catch (InvalidCipherTextException e) {
			throw new InvalidKeyException("Invalid Key.", e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("No support for UTF-8 available.", e);
		}
	}

	private static byte[] createPwdCheck(SecretKey skey)
			throws InvalidKeyException, UnsupportedEncodingException {
		byte[] iv = new byte[IV_LENGTH];
		SecureRandom random = new SecureRandom();
		random.nextBytes(iv);
		AEADBlockCipher ecipher = CryptoHelper.getCipher();
		byte[] checkData;
		try {
			ecipher.init(true,
					new AEADParameters(new KeyParameter(skey.getEncoded()),
							CryptoHelper.MAC_SIZE, iv));

			// create random integer with checksum (UPC-Format : 12 digits)
			String testValue = String.format("%011d",
					random.nextInt(Integer.MAX_VALUE))
					+ "0";
			int res1 = calcUpcChecksum(testValue);

			testValue = testValue.substring(0, UPC_NR_LEN) + res1;

			byte[] pwdCheck = CryptoHelper.cipherData(ecipher,
					testValue.getBytes(SyncDataHelper.DEFAULT_CHARSET_NAME));

			checkData = new byte[iv.length + pwdCheck.length];
			System.arraycopy(iv, 0, checkData, 0, iv.length);
			System.arraycopy(pwdCheck, 0, checkData, iv.length, pwdCheck.length);
		} catch (DataLengthException e) {
			throw new InvalidKeyException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new InvalidKeyException(e.getMessage(), e);
		} catch (InvalidCipherTextException e) {
			throw new InvalidKeyException(e.getMessage(), e);
		}

		return checkData;
	}

	private static int calcUpcChecksum(String testValue) {
		int res1 = 0;
		int res2 = 0;
		for (int i = 0; i < UPC_NR_LEN; i += 2) {
			res1 += Integer.parseInt(testValue.substring(i, i + 1));
		}
		for (int i = 1; i < UPC_NR_LEN; i += 2) {
			res2 += Integer.parseInt(testValue.substring(i, i + 1));
		}

		int checksum = ((res1 * UPC_MULTIPLIER + res2) % UPC_MODVAL);
		if (checksum > 0) {
			checksum = UPC_MODVAL - checksum;
		}
		return checksum;
	}

	public static String getKeyPwd(Account account,
			AccountManager accountManager) {
		return accountManager.getUserData(account, PRIVATE_PWD);
	}

	/**
	 * Clears all saved Data about the Private Key
	 * 
	 * @param account
	 * @param acManager
	 */
	public static void clearPrivateKeyData(Account account,
			AccountManager acManager) {
		acManager.setUserData(account, PRIVATE_KEYSALT, null);
		acManager.setUserData(account, PRIVATE_PWDCHECK, null);
		acManager.setUserData(account, PRIVATE_PWD, null);
		acManager.setUserData(account, PRIVATE_KEY, null);
		acManager.setUserData(account, PRIVATE_KEY_SALTSAVED, null);
	}

	/**
	 * Checks if the key-data is ready for sync: Private Key is available for
	 * encryption or not another key is already save on the server.
	 * 
	 * @param context
	 * @param account
	 * @param am
	 * @param authToken
	 * @return false is Sync is not ready. Additionally input will be required.
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 */
	public static PrivateKeyState isReadyForSync(Context context,
			Account account, AccountManager am, String authToken)
			throws OperationCanceledException {
		SecretKey key = getPrivateKey(account, am);
		PrivateKeyState state = PrivateKeyState.READY;
		if (key == null || !ClientKeyHelper.isSaltSaved(account, am)) {
			try {
				// Check ob PwdSalt von einem anderen Client vorhanden
				// ist
				byte[] saltPwdCheck = NetworkUtilities.getKeySalt(context,
						account.name, authToken);
				if (saltPwdCheck != null && saltPwdCheck.length > SALT_LENGHT) {
					// Ein Key ist bereits vorhanden -> Key muss eingegeben
					// werden.
					state = PrivateKeyState.MISSING_KEY;
					state.setCurrSalt(saltPwdCheck);
				}
			} catch (AuthenticationException e) {
				Log.w(TAG,
						"Check for PwdSalt failed with Authentification error",
						e);
				am.invalidateAuthToken(Constants.ACCOUNT_TYPE, authToken);
				state = PrivateKeyState.AUTH_FAILED;
			} catch (NetworkErrorException e) {
				Log.w(TAG,
						"Check for PwdSalt failed with NetworkErrorException.",
						e);
				state = PrivateKeyState.NETWORK_ERROR;
				state.setErrorMsg(e.getLocalizedMessage());
			} catch (ServerException e) {
				Log.w(TAG, "Check for PwdSalt failed with ServerError.", e);
				state = PrivateKeyState.CHECK_FAILED;
			}
		}
		return state;
	}

	public static enum PrivateKeyState {
		/** Private Key is Ready for Sync */
		READY,
		/**
		 * Another Key is in use, User has to enter Key-Pwd.
		 * {@link #getCurrSalt()} contains the current Key-Salt and PwdCheck
		 * from the server
		 */
		MISSING_KEY,
		/** Check failed, mostly because of an Server-Error, try later again. */
		CHECK_FAILED, AUTH_FAILED, NETWORK_ERROR;

		private byte[] currSalt;

		private String errorMsg;

		/**
		 * @return a KeySalt and PwdCheck for {@link #MISSING_KEY}
		 */
		public byte[] getCurrSalt() {
			return currSalt;
		}

		/**
		 * @param currSalt
		 *            KeySalt and PwdCheck for {@link #MISSING_KEY}
		 */
		public void setCurrSalt(byte[] currSalt) {
			this.currSalt = currSalt;
		}

		/**
		 * 
		 * @return Error-Message for {@link #NETWORK_ERROR}
		 */
		public String getErrorMsg() {
			return errorMsg;
		}

		/**
		 * @param errorMsg
		 *            Error-Message for {@link #NETWORK_ERROR}
		 */
		public void setErrorMsg(String errorMsg) {
			this.errorMsg = errorMsg;
		}
	}
}
