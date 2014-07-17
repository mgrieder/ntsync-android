package com.ntsync.android.sync.client;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on NetworkUtilities.java from the SampleSyncAdapter-Example in Android SDK
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

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.agreement.srp.SRP6Client;
import org.spongycastle.util.BigIntegers;
import org.xml.sax.SAXException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntsync.android.sync.BuildConfig;
import com.ntsync.android.sync.platform.ErrorHandler;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.ContactGroup;
import com.ntsync.shared.HeaderCreateException;
import com.ntsync.shared.HeaderParseException;
import com.ntsync.shared.Pair;
import com.ntsync.shared.PayPalConfirmationResult;
import com.ntsync.shared.Price;
import com.ntsync.shared.RawContact;
import com.ntsync.shared.RequestGenerator;
import com.ntsync.shared.RequestGenerator.SyncPrepErrorStatistic;
import com.ntsync.shared.RequestGenerator.SyncResponse;
import com.ntsync.shared.Restrictions;
import com.ntsync.shared.SRP6Helper;
import com.ntsync.shared.SyncAnchor;
import com.ntsync.shared.SyncDataHelper;
import com.ntsync.shared.UserRegistrationState;

/**
 * Provides utility methods for communicating with the server.
 */
public final class NetworkUtilities {

	private static final int INT_LEN = 4;

	/** The tag used to log to adb console. */
	private static final String TAG = "NetworkUtilities";

	private static final String SYNC_CIENTID_KEY = "com.myllih.android.sync.clientid";

	private static final String BASE_URL;

	private static final String COOKIE_SESSION_NAME = "JSESSIONID";

	static {
		if (Constants.USE_RELEASE_CONFIG) {
			BASE_URL = "https://api.ntsync.com";
		} else {
			BASE_URL = "https://192.168.0.10:8443/syncserver-web";
		}
	}

	/** URI for sync service */
	private static final String SYNC_BASE_URI = BASE_URL + "/sync";
	/** URI for authentication service */
	private static final String AUTH1_URI = SYNC_BASE_URI + "/authstep1";

	private static final String AUTH2_URI = SYNC_BASE_URI + "/authstep2";

	private static final String CREATEUSERNAME_URI = SYNC_BASE_URI
			+ "/user/createusername";

	private static final String REGISTRATION_URI = SYNC_BASE_URI
			+ "/user/registrate";

	private static final String SYNC_URI = SYNC_BASE_URI + "/send";

	private static final String PWDSALT_URI = SYNC_BASE_URI + "/salt";

	private static final String RESTRICTIONS_URI = SYNC_BASE_URI
			+ "/restrictions";

	private static final String PRICE_URI = SYNC_BASE_URI + "/price/";

	private static final String PWDVERIFYPAYMENT_URI = SYNC_BASE_URI
			+ "/paypal/verify";

	private static HttpClient client = null;

	private static final Object CL_LOCK = new Object();

	private static final Map<String, CookieStore> COOKIES = new HashMap<String, CookieStore>();

	private NetworkUtilities() {
	}

	private static HttpClient getHttpClient(Context context) {
		HttpClient newClient;
		synchronized (CL_LOCK) {
			if (client == null) {
				client = new MyHttpClient(context);
			}
			newClient = client;
		}
		return newClient;
	}

	/**
	 * CookieStore per AccountName to prevent mixing of the sessions.
	 * 
	 * @param accountName
	 *            accountName or null (default)
	 * @return
	 */
	private static HttpContext createHttpContext(String accountName,
			String authtoken) {
		BasicHttpContext ctx = new BasicHttpContext();
		CookieStore store;
		synchronized (CL_LOCK) {
			store = COOKIES.get(accountName);
			if (store == null) {
				store = new BasicCookieStore();
				COOKIES.put(accountName, store);
			}
		}
		ctx.setAttribute(ClientContext.COOKIE_STORE, store);

		if (authtoken != null) {
			boolean add = true;
			for (Cookie cookie : store.getCookies()) {
				if (COOKIE_SESSION_NAME.equals(cookie.getName())) {
					if (authtoken.equals(cookie.getValue())) {
						add = false;
					}
					break;
				}
			}
			if (add) {
				BasicClientCookie sessionCookie = new BasicClientCookie(
						COOKIE_SESSION_NAME, authtoken);
				sessionCookie.setSecure(true);
				store.addCookie(sessionCookie);
			}
		}

		return ctx;
	}

	/**
	 * Connects to the test server, authenticates the provided username and
	 * password.
	 * 
	 * @param username
	 *            The server account username
	 * @param password
	 *            The server account password
	 * @return String The authentication token returned by the server and the
	 *         SRP-Password (or null)
	 * @throws ServerException
	 * @throws NetworkErrorException
	 *             when communication failed
	 */
	public static Pair<String, byte[]> authenticate(Context context,
			String username, String password) throws NetworkErrorException,
			ServerException {
		return authenticate(context, username, password, null);
	}

	/**
	 * 
	 * @param context
	 * @param username
	 * @param srpPassword
	 * @return String The authentication token returned by the server or null
	 * @throws NetworkErrorException
	 *             when communication failed
	 * @throws ServerException
	 */
	public static String authenticate(Context context, String username,
			byte[] srpPassword) throws NetworkErrorException, ServerException {
		Pair<String, byte[]> data = authenticate(context, username, null,
				srpPassword);
		return data.left;
	}

	@SuppressLint("TrulyRandom")
	private static Pair<String, byte[]> authenticate(Context context,
			String username, String password, byte[] srpPassword)
			throws NetworkErrorException, ServerException {
		LogHelper.logD(TAG, "Authenticating to: {}", AUTH1_URI);
		if (username == null) {
			Log.e(TAG, "Username or password is null.");
			return null;
		}
		if (password == null && srpPassword == null) {
			Log.e(TAG, "Either password or srpPassword is needed.");
		}

		Pair<String, byte[]> returnData = null;

		try {
			// Step 1
			byte[] content = sendAuthStep1(context, username);
			int pwdSaltLen = SRP6Helper.PWD_SALT_LENGTH;
			if (content == null || content.length <= INT_LEN + pwdSaltLen) {
				Log.i(TAG, "Authentification failed.");
				return null;
			}
			int index = 0;
			byte[] pwdSalt = new byte[pwdSaltLen];
			System.arraycopy(content, index, pwdSalt, 0, pwdSaltLen);
			index += pwdSaltLen;
			int len = SyncDataHelper.readInt(content, index);
			index += INT_LEN;
			if (len + index >= content.length) {
				Log.i(TAG, "Invalid datastructure. ");
				return null;
			}

			byte[] salt = new byte[len];
			System.arraycopy(content, index, salt, 0, salt.length);
			index += len;

			byte[] serverB = new byte[content.length - index];
			System.arraycopy(content, index, serverB, 0, serverB.length);

			byte[] validSRPPassword = srpPassword;
			if (validSRPPassword == null) {
				validSRPPassword = SRP6Helper.createSRPPassword(password, pwdSalt);
			}

			// Step 2
			BigInteger srpB = new BigInteger(1, serverB);
			SRP6Client srpClient = new SRP6Client();
			Digest srpHashFn = SRP6Helper.createDigest();
			srpClient.init(SRP6Helper.N_2024, SRP6Helper.G_2024, srpHashFn,
					new SecureRandom());
			BigInteger srpA = srpClient.generateClientCredentials(salt,
					username.getBytes("UTF-8"), validSRPPassword);

			BigInteger clientS = srpClient.calculateSecret(srpB);
			BigInteger srpK = SRP6Helper.createHash(srpHashFn, clientS);
			BigInteger clientM = SRP6Helper.createClientM(srpHashFn, username,
					new BigInteger(1, salt), srpA, srpB, srpK);

			// Send Proof of Session and get Session-Id and Session-Prof of
			// Server
			content = sendAuthStep2(context, clientM, username, srpA);

			if (content == null || content.length == 0) {
				Log.i(TAG, "Authentification failed.");
				return null;
			}
			len = SyncDataHelper.readInt(content, 0);
			if (len + INT_LEN >= content.length) {
				Log.i(TAG, "Invalid datastructure. ");
				return null;
			}
			byte[] sessionId = new byte[len];
			System.arraycopy(content, INT_LEN, sessionId, 0, sessionId.length);
			byte[] serverMByte = new byte[content.length - INT_LEN
					- sessionId.length];
			System.arraycopy(content, INT_LEN + sessionId.length, serverMByte,
					0, serverMByte.length);
			String authtoken = new String(sessionId,
					SyncDataHelper.DEFAULT_CHARSET_NAME);
			BigInteger verifServerM = SRP6Helper.createServerM(
					SRP6Helper.createDigest(), srpA, clientM, srpK);
			BigInteger serverM = new BigInteger(1, serverMByte);
			if (!verifServerM.equals(serverM)) {
				Log.i(TAG, "Server verification failed. CalcServerM:"
						+ verifServerM + " ServerM:" + verifServerM);
				authtoken = null;
			} else if ((authtoken != null) && (authtoken.length() > 0)) {
				Log.v(TAG, "Successful authentication");
			} else {
				Log.i(TAG, "Error authenticating. Authtoken:" + authtoken);
				authtoken = null;
			}
			if (authtoken != null) {
				returnData = new Pair<String, byte[]>(authtoken, srpPassword);
			}

		} catch (CryptoException e) {
			LogHelper.logWCause(TAG, "Invalid server credentials", e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("No support for UTF-8 available.", e);
		}
		return returnData;
	}

	private static byte[] sendAuthStep2(Context context, BigInteger clientM,
			String accountName, BigInteger srpA)
			throws UnsupportedEncodingException, ServerException,
			NetworkErrorException {
		final HttpPost post = new HttpPost(AUTH2_URI);
		List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
		values.add(new BasicNameValuePair("m", Base64.encodeToString(
				BigIntegers.asUnsignedByteArray(clientM), Base64.DEFAULT)));
		values.add(new BasicNameValuePair("srpA", Base64.encodeToString(
				BigIntegers.asUnsignedByteArray(srpA), Base64.DEFAULT)));

		HttpEntity postEntity = new UrlEncodedFormEntity(values,
				SyncDataHelper.DEFAULT_CHARSET_NAME);
		post.setHeader(postEntity.getContentEncoding());
		post.setEntity(postEntity);

		try {
			final HttpResponse resp = getHttpClient(context).execute(post,
					createHttpContext(accountName, null));
			return getResponse(resp);
		} catch (IOException ex) {
			throw new NetworkErrorException(ex);
		}
	}

	private static byte[] sendAuthStep1(Context context, String username)
			throws UnsupportedEncodingException, ServerException,
			NetworkErrorException {
		final HttpPost post = new HttpPost(AUTH1_URI);
		List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
		values.add(new BasicNameValuePair("name", username));

		HttpEntity postEntity = new UrlEncodedFormEntity(values,
				SyncDataHelper.DEFAULT_CHARSET_NAME);
		post.setHeader(postEntity.getContentEncoding());
		// Explicit set no session
		post.setEntity(postEntity);
		try {
			final HttpResponse resp = getHttpClient(context).execute(post,
					createHttpContext(username, null));
			return getResponse(resp);
		} catch (IOException ex) {
			throw new NetworkErrorException(ex);
		}
	}

	private static byte[] getResponse(HttpResponse resp) throws IOException,
			ServerException {
		byte[] content = null;
		HttpEntity entity = null;
		try {
			entity = resp.getEntity();
			StatusLine statusLine = resp.getStatusLine();

			if (statusLine != null) {
				if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
					content = EntityUtils.toByteArray(entity);
				} else if (statusLine.getStatusCode() != HttpStatus.SC_UNAUTHORIZED) {
					throw new ServerException("Server error with State:"
							+ statusLine.getStatusCode());
				}
			}
		} finally {
			consumeContent(entity);
		}
		return content;
	}

	public static Pair<UserRegistrationState, String> registrateUser(
			Context context, String email, byte[] srpPassword, byte[] pwdSalt,
			String name) throws ServerException, NetworkErrorException {

		String username;
		UserRegistrationState state;
		int retryCount = 5;
		boolean retry;
		do {
			retry = false;
			state = null;
			try {
				// create new Username
				username = createUsername(context, email);
				if (username == null) {
					return new Pair<UserRegistrationState, String>(
							UserRegistrationState.EMAIL_INVALID, null);
				}
				Pair<byte[], BigInteger> verif = SRP6Helper
						.createUserVerification(username, srpPassword);

				final HttpPost post = new HttpPost(REGISTRATION_URI);
				List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
				values.add(new BasicNameValuePair("username", username));
				values.add(new BasicNameValuePair("email", email));
				values.add(new BasicNameValuePair("verif", Base64
						.encodeToString(
								BigIntegers.asUnsignedByteArray(verif.right),
								Base64.DEFAULT)));
				values.add(new BasicNameValuePair("srpSalt", Base64
						.encodeToString(verif.left, Base64.DEFAULT)));
				values.add(new BasicNameValuePair("pwdSalt", Base64
						.encodeToString(pwdSalt, Base64.DEFAULT)));
				values.add(new BasicNameValuePair("personname", name));
				values.add(new BasicNameValuePair("locale", Locale.getDefault()
						.toString()));

				HttpEntity postEntity = new UrlEncodedFormEntity(values,
						SyncDataHelper.DEFAULT_CHARSET_NAME);
				post.setHeader(postEntity.getContentEncoding());
				post.setEntity(postEntity);

				byte[] content;
				final HttpResponse resp = getHttpClient(context).execute(post,
						createHttpContext(username, null));
				content = getResponse(resp);

				if (content != null && content.length > 0) {
					String respCode = new String(content,
							SyncDataHelper.DEFAULT_CHARSET_NAME);
					state = UserRegistrationState.fromErrorVal(respCode);
				}
				if (state == null) {
					throw new ServerException("No valid response");
				}
			} catch (UnsupportedEncodingException ex) {
				throw new RuntimeException(ex);
			} catch (IOException ex) {
				throw new NetworkErrorException(ex);
			}
			if (state == UserRegistrationState.USERNAME_IN_USE) {
				// Create a new username when alredy in use (concurrent catch of
				// username)
				retry = true;
			}
			retryCount--;
		} while (retry && retryCount > 0);

		return new Pair<UserRegistrationState, String>(state, username);
	}

	private static String createUsername(Context context, String email)
			throws ServerException, NetworkErrorException,
			UnsupportedEncodingException {
		final HttpPost post = new HttpPost(CREATEUSERNAME_URI);
		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "createUsername with URI: " + post.getURI());
		}

		List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
		values.add(new BasicNameValuePair("email", email));

		HttpEntity postEntity = new UrlEncodedFormEntity(values,
				SyncDataHelper.DEFAULT_CHARSET_NAME);
		post.setHeader(postEntity.getContentEncoding());
		post.setEntity(postEntity);

		String username = null;
		byte[] content;
		try {
			final HttpResponse resp = getHttpClient(context).execute(post,
					createHttpContext(null, null));
			content = getResponse(resp);
		} catch (IOException ex) {
			throw new NetworkErrorException(ex);
		}
		if (content != null && content.length > 0) {
			username = new String(content, SyncDataHelper.DEFAULT_CHARSET_NAME);
		}
		return username;
	}

	/**
	 * 
	 * @param accountManager
	 * @param account
	 * @return null if client has not yet a clientId
	 */
	private static String getClientId(AccountManager accountManager,
			Account account) {
		String clientIdString = accountManager.getUserData(account,
				SYNC_CIENTID_KEY);
		if (!TextUtils.isEmpty(clientIdString)) {
			return clientIdString;
		}
		return null;
	}

	private static void setClientId(AccountManager accountManager,
			Account account, String clientId) {
		accountManager.setUserData(account, SYNC_CIENTID_KEY, clientId);
	}

	/**
	 * Perform 2-way sync with the server-side contacts. We send a request that
	 * includes all the locally-dirty contacts so that the server can process
	 * those changes, and we receive (and return) a list of contacts that were
	 * updated on the server-side that need to be updated locally.
	 * 
	 * @param account
	 *            The account being synced
	 * @param authtoken
	 *            The authtoken stored in the AccountManager for this account
	 * @param serverSyncState
	 *            A token returned from the server on the last sync
	 * @param dirtyContacts
	 *            A list of the contacts to send to the server
	 * @param newIdMap
	 *            Map of RawId to ServerId
	 * @param explizitPhotoSave
	 * @return A list of contacts that we need to update locally. Null if
	 *         processing of server-results failed.
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 *             when Authentication was canceled from user
	 * @throws SAXException
	 * @throws ServerException
	 * @throws NetworkErrorException
	 * @throws HeaderParseException
	 * @throws HeaderCreateException
	 */
	public static SyncResponse syncContacts(Account account, String authtoken,
			SyncAnchor serverSyncState, List<RawContact> dirtyContacts,
			List<ContactGroup> dirtyGroups, SecretKey key,
			AccountManager accountManager, Context context,
			SyncResult syncResult, String pwdSaltHexStr,
			Map<Long, String> newIdMap, Restrictions restr,
			boolean explizitPhotoSave) throws AuthenticationException,
			OperationCanceledException, AuthenticatorException,
			ServerException, NetworkErrorException, HeaderParseException,
			HeaderCreateException {
		String clientId = getClientId(accountManager, account);

		SyncPrepErrorStatistic prepError = new SyncPrepErrorStatistic();
		byte[] totBuffer = RequestGenerator.prepareServerRequest(
				serverSyncState, dirtyContacts, dirtyGroups, key,
				SystemHelper.getPkgVersion(context), clientId, pwdSaltHexStr,
				newIdMap, prepError, restr, explizitPhotoSave);
		syncResult.stats.numSkippedEntries += prepError.getIgnoredRows();
		String currAuthtoken = authtoken;

		SyncResponse syncResponse = null;
		boolean retry;
		int retrycount = 0;
		do {
			retry = false;

			HttpEntity entity = new ByteArrayEntity(totBuffer);

			// Send the updated friends data to the server
			final HttpPost post = new HttpPost(SYNC_URI);
			post.setHeader("Content-Encoding", "application/octect-stream");
			post.setEntity(entity);

			HttpEntity respEntity = null;

			try {
				final HttpResponse resp = getHttpClient(context).execute(post,
						createHttpContext(account.name, currAuthtoken));

				respEntity = resp.getEntity();
				if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					final byte[] response = EntityUtils.toByteArray(respEntity);

					syncResponse = processServerResponse(account, key,
							accountManager, clientId, response, syncResult);
					if (Log.isLoggable(TAG, Log.INFO)) {
						Log.i(TAG, "Response-Length: " + response.length);
					}
				} else {
					if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
						currAuthtoken = retryAuthentification(retrycount,
								accountManager, currAuthtoken, account.name,
								resp);
						retry = true;
					} else {
						throw new ServerException(
								"Server error in sending dirty contacts: "
										+ resp.getStatusLine());
					}
				}
			} catch (IOException ex) {
				throw new NetworkErrorException(ex);
			} finally {
				consumeContent(respEntity);
			}
			retrycount++;
		} while (retry);

		return syncResponse;
	}

	private static SyncResponse processServerResponse(Account account,
			SecretKey key, AccountManager accountManager, String clientId,
			final byte[] response, SyncResult syncResult)
			throws HeaderParseException {
		SyncResponse serverResponse = RequestGenerator.processServerResponse(
				key, clientId, response);
		// Neue ClientId übernehmen
		if (serverResponse.clientId != null && TextUtils.isEmpty(clientId)) {
			setClientId(accountManager, account, serverResponse.clientId);
		}

		syncResult.stats.numSkippedEntries = +serverResponse.skippedResponse;

		return serverResponse;
	}

	/**
	 * 
	 * @param authtoken
	 * @return KeySalt and KeyCheck. null if server was returning nothing
	 * @throws IOException
	 * @throws AuthenticationException
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 * @throws ServerException
	 * @throws NetworkErrorException
	 */
	public static byte[] getKeySalt(Context context, String accountName,
			String authtoken) throws AuthenticationException,
			OperationCanceledException, ServerException, NetworkErrorException {

		boolean retry;
		int retryCount = 0;
		String currAuthtoken = authtoken;
		byte[] pwdSalt = null;
		do {
			retry = false;
			final HttpGet get = new HttpGet(PWDSALT_URI);
			HttpEntity entity = null;
			try {
				final HttpResponse resp = getHttpClient(context).execute(get,
						createHttpContext(accountName, currAuthtoken));
				entity = resp.getEntity();
				int statusCode = resp.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK) {
					byte[] respBytes = EntityUtils.toByteArray(entity);
					if (BuildConfig.DEBUG) {
						Log.v(TAG, "Get Response-Lenght:"
								+ (respBytes != null ? respBytes.length
										: "null"));
					}
					pwdSalt = respBytes == null || respBytes.length == 0 ? null
							: respBytes;
				} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					AccountManager accountManager = AccountManager.get(context);
					currAuthtoken = retryAuthentification(retryCount,
							accountManager, currAuthtoken, accountName, resp);
					retry = true;
				} else {
					throw new ServerException(
							"Server error in query getPwdSalt: "
									+ resp.getStatusLine());
				}
			} catch (IOException ex) {
				throw new NetworkErrorException(ex);
			} finally {
				consumeContent(entity);
			}
			retryCount++;
		} while (retry);
		return pwdSalt;
	}

	private static void consumeContent(HttpEntity entity) {
		try {
			if (entity != null) {
				entity.consumeContent();
			}
		} catch (IOException ex) {
			LogHelper.logW(TAG, "Could not consume HttpEntity", ex);
		}
	}

	private static String retryAuthentification(int retryCount,
			AccountManager accountManager, String authtoken,
			String accountName, HttpResponse response)
			throws AuthenticationException, OperationCanceledException,
			NetworkErrorException, ServerException {
		accountManager.invalidateAuthToken(Constants.ACCOUNT_TYPE, authtoken);
		String newToken = null;
		if (retryCount == 0) {
			newToken = blockingGetAuthToken(accountManager, new Account(
					accountName, Constants.ACCOUNT_TYPE), null);
		}
		if (newToken == null) {
			throw new AuthenticationException(response.getStatusLine()
					.toString());
		}
		return newToken;
	}

	/**
	 * 
	 * @param authtoken
	 * @return null if server returned no restrictions.
	 * @throws IOException
	 * @throws AuthenticationException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 * @throws NetworkErrorException
	 * @throws ServerException
	 */
	public static Restrictions getRestrictions(Context context,
			Account account, String authtoken, AccountManager accountManager)
			throws AuthenticationException, OperationCanceledException,
			AuthenticatorException, NetworkErrorException, ServerException {
		String currAuthtoken = authtoken;
		HttpEntity entity = null;
		Restrictions restr = null;
		boolean retry;
		int retryCount = 0;
		do {
			retry = false;

			final HttpGet get = new HttpGet(RESTRICTIONS_URI);
			try {
				final HttpResponse resp = getHttpClient(context).execute(get,
						createHttpContext(account.name, currAuthtoken));

				entity = resp.getEntity();
				int statusCode = resp.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK) {
					byte[] respBytes = EntityUtils.toByteArray(entity);
					if (BuildConfig.DEBUG) {
						Log.v(TAG, "Get Response-Lenght:"
								+ (respBytes != null ? respBytes.length
										: "null"));
					}
					if (respBytes != null && respBytes.length > 0) {
						restr = RequestGenerator
								.parseRestr(new ByteArrayInputStream(respBytes));
					}
				} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					currAuthtoken = retryAuthentification(retryCount,
							accountManager, currAuthtoken, account.name, resp);
					retry = true;
				} else {
					throw new ServerException(
							"Server error in query getRestrictions: "
									+ resp.getStatusLine());
				}
			} catch (IOException e) {
				throw new NetworkErrorException(e);
			} finally {
				consumeContent(entity);
			}
			retryCount++;
		} while (retry);
		return restr;
	}

	/**
	 * Get current Prices for a Currency
	 * 
	 * @param authtoken
	 *            can be null
	 * @return null if server returned no prices.
	 * @throws IOException
	 * @throws AuthenticationException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 * @throws ServerException
	 * @throws NetworkErrorException
	 */
	public static List<Price> getPrices(Context context, Account account,
			String authtoken, AccountManager accountManager, String currency)
			throws OperationCanceledException, AuthenticationException,
			NetworkErrorException, ServerException {
		String currAuthtoken = authtoken;
		HttpEntity entity = null;
		List<Price> prices = null;
		boolean retry;
		int retryCount = 0;
		do {
			retry = false;

			final HttpGet get = new HttpGet(PRICE_URI + currency);
			LogHelper.logD(TAG, "getPrices with URI: {}", get.getURI());
			try {

				final HttpResponse resp = getHttpClient(context).execute(get,
						createHttpContext(account.name, currAuthtoken));

				entity = resp.getEntity();
				int statusCode = resp.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK) {
					ObjectMapper mapper = new ObjectMapper();
					Class<?> clz = Price.class;
					JavaType type = mapper.getTypeFactory()
							.constructCollectionType(List.class, clz);
					byte[] respBytes = EntityUtils.toByteArray(entity);
					prices = mapper.readValue(respBytes, type);

				} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					currAuthtoken = retryAuthentification(retryCount,
							accountManager, currAuthtoken, account.name, resp);
					retry = true;
				} else {
					throw new ServerException(
							"Server error in query getPrices: "
									+ resp.getStatusLine());
				}
			} catch (IOException e) {
				throw new NetworkErrorException(e);
			} finally {
				consumeContent(entity);
			}
			retryCount++;
		} while (retry);
		return prices;
	}

	public static boolean savePwdSalt(Context context, String accountName,
			String authtoken, String newSalt, String oldSalt,
			boolean clearData, String pwdCheck) throws AuthenticationException,
			ServerException, NetworkErrorException {
		final HttpPost post = new HttpPost(PWDSALT_URI);
		List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
		values.add(new BasicNameValuePair("newSalt", newSalt == null ? ""
				: newSalt));
		values.add(new BasicNameValuePair("oldSalt", oldSalt == null ? ""
				: oldSalt));
		values.add(new BasicNameValuePair("clearData", String
				.valueOf(clearData)));
		values.add(new BasicNameValuePair("pwdCheck", pwdCheck == null ? ""
				: pwdCheck));

		HttpResponse resp = null;
		final String respStr;
		HttpEntity entity = null;
		try {
			HttpEntity postEntity = new UrlEncodedFormEntity(values,
					SyncDataHelper.DEFAULT_CHARSET_NAME);
			post.setHeader(postEntity.getContentEncoding());
			post.setEntity(postEntity);

			resp = getHttpClient(context).execute(post,
					createHttpContext(accountName, authtoken));
			entity = resp.getEntity();
			respStr = EntityUtils.toString(entity);
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex);
		} catch (IOException ex) {
			throw new NetworkErrorException(ex);
		} finally {
			if (entity != null) {
				try {
					entity.consumeContent();
				} catch (IOException ex) {
					LogHelper.logD(TAG,
							"Close Entity failed with: " + ex.getMessage(), ex);
				}
			}
		}

		boolean saltSaved = true;
		StatusLine statusLine = resp.getStatusLine();
		int statusCode = statusLine.getStatusCode();
		if (statusCode != HttpStatus.SC_OK) {
			if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
				throw new AuthenticationException(statusLine.toString());
			} else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
				saltSaved = false;
			} else {
				throw new ServerException("Server error in query savePwdSalt: "
						+ statusLine);
			}
		} else {
			if (respStr != null && !respStr.startsWith("OK")) {
				Log.w(TAG, "SaltSaved failed with: " + respStr);
				saltSaved = false;
			}
		}
		return saltSaved;
	}

	/**
	 * 
	 * @param acm
	 * @param account
	 * 
	 * 
	 * @param Activity
	 *            if null show a notification when Login is needed otherwise
	 *            show the Login-Activity in the context of the provided
	 *            Activity. *
	 * @return SessionId
	 * @throws OperationCanceledException
	 * @throws ServerException
	 * @throws NetworkErrorException
	 */
	@SuppressWarnings("deprecation")
	public static String blockingGetAuthToken(AccountManager acm,
			Account account, Activity activity)
			throws OperationCanceledException, ServerException,
			NetworkErrorException {
		String authToken = null;
		try {
			Bundle result;
			if (activity == null) {
				// New is available from API 14 -> use deprecated API
				result = acm.getAuthToken(account, Constants.AUTHTOKEN_TYPE,
						true, null, null).getResult();
			} else {
				result = acm.getAuthToken(account, Constants.AUTHTOKEN_TYPE,
						null, activity, null, null).getResult();
			}
			if (result != null) {
				if (result.containsKey(AccountManager.KEY_AUTHTOKEN)) {
					authToken = result.getString(AccountManager.KEY_AUTHTOKEN);
				}
				if (result.containsKey(AccountManager.KEY_ERROR_CODE)) {
					int errorCode = result.getInt(
							AccountManager.KEY_ERROR_CODE, -1);
					String msg = result
							.getString(AccountManager.KEY_ERROR_MESSAGE);
					if (errorCode == Constants.AUTH_ERRORCODE_SERVEREXCEPTION) {
						throw new ServerException(msg);
					} else {
						LogHelper.logE(TAG,
								"Authentification failed with unknown errorCode:"
										+ errorCode + " Message:" + msg, null);
					}
				}
			}
		} catch (AuthenticatorException e) {
			LogHelper.logE(TAG, "Authentification failed.", e);
			// Should not happen -> report error
			ErrorHandler.reportException(e);
		} catch (IOException ex) {
			throw new NetworkErrorException(ex);
		}
		return authToken;
	}

	/**
	 * Send a PayPalPayment to the server for verification an processing.
	 * 
	 * @param priceId
	 * @param jsonProofOfPayment
	 * @return
	 * @throws IOException
	 * @throws AuthenticationException
	 * @throws AuthenticatorException
	 * @throws OperationCanceledException
	 * @throws ServerException
	 * @throws NetworkErrorException
	 */
	public static PayPalConfirmationResult verifyPayPalPayment(Context context,
			Account account, String priceId, String jsonProofOfPayment,
			String authtoken, AccountManager accountManager)
			throws AuthenticationException, OperationCanceledException,
			ServerException, NetworkErrorException {

		String currAuthtoken = authtoken;
		HttpEntity entity;
		PayPalConfirmationResult result = null;

		boolean retry;
		int retryCount = 0;
		do {
			retry = false;
			entity = null;

			final HttpPost post = new HttpPost(PWDVERIFYPAYMENT_URI);
			List<BasicNameValuePair> values = new ArrayList<BasicNameValuePair>();
			values.add(new BasicNameValuePair("priceid", priceId));
			values.add(new BasicNameValuePair("confirmation",
					jsonProofOfPayment));

			HttpResponse resp = null;
			final String respStr;
			try {
				HttpEntity postEntity = new UrlEncodedFormEntity(values,
						SyncDataHelper.DEFAULT_CHARSET_NAME);
				post.setHeader(postEntity.getContentEncoding());
				post.setEntity(postEntity);

				resp = getHttpClient(context).execute(post,
						createHttpContext(account.name, authtoken));
				entity = resp.getEntity();
				respStr = EntityUtils.toString(entity);

				StatusLine statusLine = resp.getStatusLine();
				int statusCode = statusLine.getStatusCode();
				if (statusCode == HttpStatus.SC_OK) {
					result = PayPalConfirmationResult.fromErrorVal(respStr);
				} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					currAuthtoken = retryAuthentification(retryCount,
							accountManager, currAuthtoken, account.name, resp);
					retry = true;
				} else {
					throw new ServerException(
							"Server error in query verifyPayPalPayment: "
									+ resp.getStatusLine());
				}

			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new NetworkErrorException(e);
			} finally {
				consumeContent(entity);
			}
			retryCount++;
		} while (retry);

		return result;
	}
}
