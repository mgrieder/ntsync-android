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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.security.cert.X509Certificate;

import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.util.Log;

import com.ntsync.android.sync.BuildConfig;

/**
 * Custom SSLSocketFactory which doesn't verify the SSL-Hostname when
 * <code>BuildConfig.DEBUG</code> is <code>true</code>
 */
class MySSLSocketFactory implements SocketFactory, LayeredSocketFactory {
	private SSLContext sslcontext = null;

	/** The tag used to log to adb console. */

	private static final String TAG = "MySSLSocketFactory";

	private static final String CNSTR = "CN=";

	private static SSLContext createEasySSLContext() throws IOException {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new MyX509TrustManager() },
					null);
			return context;
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "Init SSLContext failed.", e);
			throw new IOException(e.getMessage());
		}
	}

	private SSLContext getSSLContext() throws IOException {
		if (this.sslcontext == null) {
			this.sslcontext = createEasySSLContext();
		}
		return this.sslcontext;
	}

	public Socket connectSocket(Socket sock, String host, int port,
			InetAddress localAddress, int localPort, HttpParams params)
			throws IOException {
		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);
		InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
		SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket());

		if ((localAddress != null) || (localPort > 0)) {
			// we need to bind explicitly
			int validLocalPort = localPort;
			if (validLocalPort < 0) {
				validLocalPort = 0; // indicates "any"
			}
			InetSocketAddress isa = new InetSocketAddress(localAddress,
					validLocalPort);
			sslsock.bind(isa);
		}

		sslsock.connect(remoteAddress, connTimeout);
		sslsock.setSoTimeout(soTimeout);

		return sslsock;
	}

	public Socket createSocket() throws IOException {
		Socket newSocket = getSSLContext().getSocketFactory().createSocket();
		if (newSocket instanceof SSLSocket) {
			verifyHostname((SSLSocket) newSocket);
		}
		return newSocket;
	}

	public boolean isSecure(Socket socket) {
		return true;
	}

	private void verifyHostname(SSLSocket socket)
			throws SSLPeerUnverifiedException {
		SSLSession session = socket.getSession();
		String hostname = session.getPeerHost();

		X509Certificate[] certs = session.getPeerCertificateChain();
		if (certs == null || certs.length == 0) {
			throw new SSLPeerUnverifiedException(
					"No server certificates found!");
		}

		// get the servers DN in its string representation
		String dn = certs[0].getSubjectDN().getName();

		// might be useful to print out all certificates we receive from the
		// server, in case one has to debug a problem with the installed certs.
		if (Log.isLoggable(TAG, Log.DEBUG)) {
			Log.d(TAG, "Server certificate chain:");
			for (int i = 0; i < certs.length; i++) {
				Log.d(TAG, "X509Certificate[" + i + "]=" + certs[i]);
			}
		}
		// get the common name from the first cert
		String cn = getCN(dn);
		if (hostname != null && hostname.equalsIgnoreCase(cn)) {
			if (Log.isLoggable(TAG, Log.DEBUG)) {
				Log.d(TAG, "Target hostname valid: " + cn);
			}
		} else {
			if (BuildConfig.DEBUG) {
				Log.w(TAG, "HTTPS hostname invalid: expected '" + hostname
						+ "', received '" + cn + "'");
				return;
			}
			throw new SSLPeerUnverifiedException(
					"HTTPS hostname invalid: expected '" + hostname
							+ "', received '" + cn + "'");
		}
	}

	/**
	 * Parses a X.500 distinguished name for the value of the "Common Name"
	 * field. This is done a bit sloppy right now and should probably be done a
	 * bit more according to <code>RFC 2253</code>.
	 * 
	 * @param dn
	 *            a X.500 distinguished name.
	 * @return the value of the "Common Name" field.
	 */
	private String getCN(String dn) {
		int i = 0;
		if (dn == null) {
			return null;
		}

		i = dn.indexOf(CNSTR);
		if (i == -1) {
			return null;
		}
		// get the remaining DN without CN=

		int len = dn.length();
		int pos2 = len;
		for (int j = i + CNSTR.length(); j < len; j++) {
			if (dn.charAt(j) == ',' && j > 0 && dn.charAt(j - 1) != '\\') {
				pos2 = j;
				break;
			}
		}
		return dn.substring(i + CNSTR.length(), pos2);
	}

	public Socket createSocket(Socket socket, String host, int port,
			boolean autoClose) throws IOException {
		Socket newSocket = getSSLContext().getSocketFactory().createSocket(
				socket, host, port, autoClose);
		if (newSocket instanceof SSLSocket) {
			verifyHostname((SSLSocket) newSocket);
		}
		return newSocket;

	}

	@Override
	public boolean equals(Object obj) {
		return ((obj != null) && obj.getClass().equals(this.getClass()));
	}

	@Override
	public int hashCode() {
		return MySSLSocketFactory.class.hashCode();
	}
}