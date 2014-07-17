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

import java.security.SecureRandom;

import android.accounts.NetworkErrorException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.Pair;
import com.ntsync.shared.SRP6Helper;
import com.ntsync.shared.UserRegistrationState;

/**
 * Performs the Registration with the Sync-Server
 */
public class RegistrateProgressDialog extends DialogFragment {

	/** The tag used to log to adb console. */
	private static final String TAG = "CreatePwdProgressDialog";

	public static final String PARAM_PWD = "pwd";

	public static final String PARAM_EMAIL = "email";

	public static final String PARAM_NAME = "name";

	private AsyncTask<Void, Void, Exception> registrateTask = null;

	private UserRegistrationState state = null;
	private boolean canceled = false;
	private RegistrationError taskError = null;
	private String email = null;
	private String password = null;
	private String username = null;
	private String name = null;
	private byte[] srpPassword = null;
	private byte[] pwdSalt = null;

	/**
	 * Creates a Registrate Progress Dialog
	 * 
	 * @param userName
	 * @param pwdSalt
	 * @param authtoken
	 * @return invisible Dialog
	 */
	public static RegistrateProgressDialog newInstance(String email,
			String password, String name) {
		RegistrateProgressDialog dlg = new RegistrateProgressDialog();

		Bundle args = new Bundle();
		args.putString(PARAM_PWD, password);
		args.putString(PARAM_EMAIL, email);
		args.putString(PARAM_NAME, name);

		dlg.setArguments(args);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		password = args.getString(PARAM_PWD);
		email = args.getString(PARAM_EMAIL);
		name = args.getString(PARAM_NAME);

		// Retain to keep Task during conf changes
		setRetainInstance(true);
		setCancelable(false);

		registrateTask = new RegisterTask();
		registrateTask.execute();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getText(R.string.register_activity_progress));
		dialog.setIndeterminate(true);
		return dialog;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (registrateTask != null
				&& registrateTask.getStatus() == Status.FINISHED) {
			deliverResult();
		}
	}

	protected void deliverResult() {
		// deliver result otherwise in onResume, when killed task will be
		// started again.
		if (isResumed()) {
			dismiss();
			if (resultListener != null) {
				resultListener.onCreateEnd(state, canceled, taskError, email,
						srpPassword, username);
			}
		}
	}

	/**
	 * The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 */
	public interface RegistrateDialogListener {
		/**
		 * 
		 * @param success
		 * @param cancel
		 * @param error
		 *            if success is false, contains an error
		 */
		void onCreateEnd(UserRegistrationState state, boolean cancel,
				RegistrationError error, String email, byte[] srpPassword,
				String username);

	}

	public enum RegistrationError {
		NETWORK_ERROR, REGISTRATION_FAILED, SERVER_ERROR
	}

	// Use this instance of the interface to deliver action events
	private RegistrateDialogListener resultListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			resultListener = (RegistrateDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			ClassCastException ex = new ClassCastException(activity.toString()
					+ " must implement RegistrateDialogListener");
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Represents an asynchronous task used to create Password
	 */
	private class RegisterTask extends AsyncTask<Void, Void, Exception> {

		private Pair<UserRegistrationState, String> result = null;

		public RegisterTask() {
		}

		@SuppressLint("TrulyRandom")
		@Override
		protected Exception doInBackground(Void... params) {
			Exception ex = null;
			if (!isCancelled()) {
				try {
					pwdSalt = new byte[SRP6Helper.PWD_SALT_LENGTH];
					SecureRandom random = new SecureRandom();
					random.nextBytes(pwdSalt);

					srpPassword = SRP6Helper.createSRPPassword(password,
							pwdSalt);

					result = NetworkUtilities.registrateUser(
							RegistrateProgressDialog.this.getActivity(), email,
							srpPassword, pwdSalt, name);
				} catch (ServerException e) {
					ex = e;
					LogHelper.logWCause(TAG, "Registration failed.", e);
				} catch (NetworkErrorException e) {
					ex = e;
					LogHelper.logW(TAG, "Registration failed.", e);
				}
			}
			return ex;
		}

		@Override
		protected void onPostExecute(final Exception exception) {
			super.onPostExecute(exception);
			canceled = isCancelled();
			if (exception == null) {
				if (result != null) {
					state = result.left;
					username = result.right;
				} else {
					state = null;
					username = null;
					pwdSalt = null;
					srpPassword = null;
				}
				taskError = null;
			} else {
				RegistrationError error = RegistrationError.REGISTRATION_FAILED;
				if (exception instanceof NetworkErrorException) {
					error = RegistrationError.NETWORK_ERROR;
				} else if (exception instanceof ServerException) {
					error = RegistrationError.SERVER_ERROR;
				}
				state = null;
				taskError = error;
				username = null;
				pwdSalt = null;
				srpPassword = null;
			}
			deliverResult();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			state = null;
			canceled = true;
			taskError = null;
			username = null;
			pwdSalt = null;
			srpPassword = null;
			deliverResult();
		}
	}
}
