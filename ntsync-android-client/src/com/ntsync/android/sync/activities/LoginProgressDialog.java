package com.ntsync.android.sync.activities;

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

import org.apache.http.auth.AuthenticationException;

import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.Pair;

/**
 * Performing Login with Sync-Server
 */
public class LoginProgressDialog extends DialogFragment {

	/** The tag used to log to adb console. */
	private static final String TAG = "LoginProgressDialog";

	private static final String PARAM_PWD = "pwd";

	private static final String PARAM_USERNAME = "username";

	private AsyncTask<Void, Void, LoginData> loginTask = null;

	private boolean success = false;
	private boolean canceled = false;
	private LoginError taskError = null;
	private LoginData loginData = null;

	/**
	 * Creates a new Key
	 * 
	 * @param userName
	 * @param pwdSalt
	 * @param authtoken
	 * @return invisible Dialog
	 */
	public static LoginProgressDialog newInstance(String username,
			String password) {
		LoginProgressDialog dlg = new LoginProgressDialog();

		Bundle args = new Bundle();
		args.putString(PARAM_USERNAME, username);
		args.putString(PARAM_PWD, password);

		dlg.setArguments(args);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		String username = args.getString(PARAM_USERNAME);
		String password = args.getString(PARAM_PWD);

		// Retain to keep Task during conf changes
		setRetainInstance(true);
		setCancelable(false);

		loginTask = new UserLoginTask(username, password);
		loginTask.execute();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getText(R.string.ui_activity_authenticating));
		dialog.setIndeterminate(true);
		return dialog;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (loginTask != null && loginTask.getStatus() == Status.FINISHED) {
			deliverResult();
		}
	}

	/**
	 * Sends Dialog Result to Listener if Dialog is active, otherwise should be
	 * called again in onResume() when Task is finished
	 */
	protected void deliverResult() {
		// deliver result otherwise in onResume, when killed task will be
		// started again.
		if (isResumed()) {
			dismiss();
			if (resultListener != null) {
				String token = loginData != null ? loginData.authtoken : null;
				byte[] keySaltCheck = loginData != null ? loginData.keySaltAndKeyCheck
						: null;
				byte[] srpPassword = loginData != null ? loginData.srpPassword
						: null;

				resultListener.onCreateEnd(success, canceled, taskError, token,
						keySaltCheck, srpPassword);
			}
		}
	}

	/**
	 * The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 */
	public interface LoginDialogListener {
		/**
		 * 
		 * @param success
		 * @param cancel
		 * @param error
		 *            if success is false, contains an error
		 */
		void onCreateEnd(boolean success, boolean cancel, LoginError error,
				String authtoken, byte[] keySaltAndKeyCheck, byte[] srpPassword);
	}

	public enum LoginError {
		NETWORK_ERROR, SERVER_ERROR, LOGIN_FAILED;
	}

	// Use this instance of the interface to deliver action events
	private LoginDialogListener resultListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			resultListener = (LoginDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			ClassCastException ex = new ClassCastException(activity.toString()
					+ " must implement LoginDialogListener");
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Represents an asynchronous task used to authenticate a user against the
	 */
	public final class UserLoginTask extends AsyncTask<Void, Void, LoginData> {

		private String username;
		private String password;

		private UserLoginTask(String username, String password) {
			this.username = username;
			this.password = password;
		}

		private Exception ex;

		@Override
		protected LoginData doInBackground(Void... params) {
			LoginData taskResult = null;
			try {
				Context context = LoginProgressDialog.this.getActivity();

				Pair<String, byte[]> authData = NetworkUtilities.authenticate(
						context, username, password);
				if (authData != null && authData.left != null) {
					String token = authData.left;
					byte[] saltPwdCheck = NetworkUtilities.getKeySalt(context,
							username, token);
					taskResult = new LoginData(token, saltPwdCheck,
							authData.right);
				}
			} catch (NetworkErrorException ex) {
				LogHelper.logI(TAG, "Failed to authenticate", ex);
				this.ex = ex;
			} catch (ServerException e) {
				LogHelper.logWCause(TAG,
						"Failed to authenticate due to a server error", e);
				this.ex = e;
			} catch (AuthenticationException e) {
				LogHelper.logWCause(TAG, "Authentication failed", e);
				this.ex = e;
			} catch (OperationCanceledException e) {
				LogHelper.logI(TAG, "Operation canceled", e);
			}
			return taskResult;
		}

		@Override
		protected void onPostExecute(final LoginData result) {
			super.onPostExecute(result);

			loginData = result;
			canceled = false;
			if (ex == null) {
				success = (loginData != null && loginData.authtoken != null)
						&& (loginData.authtoken.length() > 0);
				taskError = null;
			} else {
				Exception exception = ex;
				LoginError error = LoginError.LOGIN_FAILED;
				if (exception instanceof NetworkErrorException) {
					error = LoginError.NETWORK_ERROR;
				} else if (exception instanceof ServerException) {
					error = LoginError.SERVER_ERROR;
				}
				success = false;
				taskError = error;
			}
			deliverResult();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			success = false;
			taskError = null;
			loginData = null;
			deliverResult();
		}
	}

	private static class LoginData {
		public final String authtoken;
		public final byte[] keySaltAndKeyCheck;
		public final byte[] srpPassword;

		public LoginData(String authtoken, byte[] keySaltAndKeyCheck,
				byte[] srpPassword) {
			super();
			this.authtoken = authtoken;
			this.keySaltAndKeyCheck = keySaltAndKeyCheck;
			this.srpPassword = srpPassword;
		}
	}
}
