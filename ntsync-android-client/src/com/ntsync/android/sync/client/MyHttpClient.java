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
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;

class MyHttpClient extends DefaultHttpClient {

	private static final String TAG = "MyHttpClient";

	/** Timeout (in ms) we specify for each http request */
	private static final int CONNECTION_TIMEOUT = 10 * 1000;

	private static final int READ_TIMEOUT_MS = 600 * 1000;

	private static final int GET_CONNECTION_MS = 10 * 1000;

	private Context context;

	public MyHttpClient(Context context) {
		super();
		this.context = context;
	}

	private String getUserAgent(Context context,
			String defaultHttpClientUserAgent) {
		String versionName = SystemHelper.getPkgVersion(context);
		StringBuilder ret = new StringBuilder();
		ret.append("NTsync");
		ret.append("/");
		ret.append(versionName);
		ret.append(" (");
		ret.append("Linux; U; Android ");
		ret.append(Build.VERSION.RELEASE);
		ret.append("; ");
		ret.append(Locale.getDefault());
		ret.append("; ");
		ret.append(Build.PRODUCT);
		ret.append(")");
		if (defaultHttpClientUserAgent != null) {
			ret.append(" ");
			ret.append(defaultHttpClientUserAgent);
		}
		return ret.toString();
	}

	protected ClientConnectionManager createClientConnectionManager() {
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", new PlainSocketFactory(), 80));
		registry.register(new Scheme("https", getSSLSocketFactory(), 443));

		HttpParams params = getParams();
		HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
		HttpConnectionParams.setSoTimeout(params, READ_TIMEOUT_MS);
		ConnManagerParams.setTimeout(params, GET_CONNECTION_MS);
		HttpProtocolParams.setUserAgent(params,
				getUserAgent(context, HttpProtocolParams.getUserAgent(params)));

		return new ThreadSafeClientConnManager(params, registry);
	}

	private SocketFactory getSSLSocketFactory() {
		InputStream in = null;
		SocketFactory socketFack = null;
		try {
			KeyStore trusted = KeyStore.getInstance("BKS");
			in = context.getResources().openRawResource(R.raw.mykeystore);
			trusted.load(in, "pwd23key".toCharArray());
			SSLSocketFactory sslSocketFack = new SSLSocketFactory(trusted);
			socketFack = sslSocketFack;
			if (Constants.USE_RELEASE_CONFIG) {
				sslSocketFack
						.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
			} else {
				Log.w(TAG, "Disable SSL Hostname verification");
				sslSocketFack
						.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			}
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "Loading truststore failed.", e);
		} catch (IOException e) {
			Log.e(TAG, "Loading truststore failed.", e);
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "closing filescocket failed.", e);
			}
		}
		if (socketFack == null) {
			Log.w(TAG, "Fallback to custom ssl socket factory.");
			socketFack = new MySSLSocketFactory();
		}
		return socketFack;
	}
}