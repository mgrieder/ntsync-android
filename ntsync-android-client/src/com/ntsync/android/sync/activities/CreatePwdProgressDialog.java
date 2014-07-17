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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;

import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Base64;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.ClientKeyHelper;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.Pair;

/**
 * Progress Dialog with Task for Creating a new Private Key with optional
 * validation based on a current Private Key-Salt and PwdCheck (New Create /
 * Recreate)
 * 
 */
public class CreatePwdProgressDialog extends DialogFragment {

	/** The tag used to log to adb console. */
	private static final String TAG = "CreatePwdProgressDialog";

	public static final String PARAM_PWD = "pwd";

	private AsyncTask<Void, Void, Exception> createTask = null;

	private boolean success = false;

	private boolean canceled = false;

	private CreatePwdError taskError = null;

	/**
	 * Creates a new Key
	 * 
	 * @param userName
	 * @param pwdSalt
	 * @param authtoken
	 * @return invisible Dialog
	 */
	public static CreatePwdProgressDialog newInstance(String userName,
			byte[] currPwdSalt) {
		CreatePwdProgressDialog dlg = new CreatePwdProgressDialog();

		Bundle args = new Bundle();
		args.putString(KeyPasswordActivity.PARAM_USERNAME, userName);
		args.putByteArray(KeyPasswordActivity.PARAM_SALT, currPwdSalt);

		dlg.setArguments(args);
		return dlg;
	}

	/**
	 * Recreates a Key based on a existing Password
	 * 
	 * @param userName
	 * @param pwdSalt
	 * @param authtoken
	 * @return invisible Dialog
	 */
	public static CreatePwdProgressDialog newInstance(String userName,
			String pwd, byte[] currPwdSalt, byte[] pwdCheck) {
		CreatePwdProgressDialog dlg = new CreatePwdProgressDialog();

		Bundle args = new Bundle();
		args.putString(KeyPasswordActivity.PARAM_USERNAME, userName);
		args.putString(PARAM_PWD, pwd);
		args.putByteArray(KeyPasswordActivity.PARAM_SALT, currPwdSalt);
		args.putByteArray(KeyPasswordActivity.PARAM_CHECK, pwdCheck);
		if (pwd == null) {
			throw new IllegalArgumentException("pwd is null");
		}

		dlg.setArguments(args);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		AccountManager accountManager = AccountManager.get(getActivity());
		Bundle args = getArguments();
		String mUsername = args.getString(KeyPasswordActivity.PARAM_USERNAME);
		byte[] pwdSalt = args.getByteArray(KeyPasswordActivity.PARAM_SALT);
		Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);

		String pwd = args.getString(PARAM_PWD);
		byte[] pwdCheck = args.getByteArray(KeyPasswordActivity.PARAM_CHECK);

		// Retain to keep Task during conf changes
		setRetainInstance(true);
		setCancelable(false);

		if (pwd == null) {
			createTask = new CreatePwdTask(account, accountManager, pwdSalt);
		} else {
			createTask = new RecreateKeyTask(account, accountManager, pwdSalt,
					pwdCheck, pwd);
		}
		createTask.execute();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getText(R.string.keypwd_activity_createpwd));
		dialog.setIndeterminate(true);

		return dialog;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (createTask != null && createTask.getStatus() == Status.FINISHED) {
			deliverResult();
		}
	}

	protected void deliverResult() {
		// deliver result otherwise in onResume, when killed task will be
		// started again.
		if (isResumed()) {
			dismiss();
			if (resultListener != null) {
				resultListener.onCreateEnd(success, canceled, taskError);
			}
		}
	}

	/**
	 * The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 */
	public interface CreatePwdDialogListener {
		/**
		 * 
		 * @param success
		 * @param cancel
		 * @param error
		 *            if success is false, contains an error
		 */
		void onCreateEnd(boolean success, boolean cancel, CreatePwdError error);
	}

	/**
	 * Key Generation failed with an Error
	 */
	public enum CreatePwdError {
		NETWORK_ERROR, KEY_GEN_FAILED, SERVER_ERROR, KEY_VALIDATION_FAILED
	}

	// Use this instance of the interface to deliver action events
	private CreatePwdDialogListener resultListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			resultListener = (CreatePwdDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			ClassCastException ex = new ClassCastException(activity.toString()
					+ " must implement CreatePwdDialogListener");
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Represents an asynchronous task used to create Password
	 */
	private class CreatePwdTask extends AsyncTask<Void, Void, Exception> {

		private Account account;
		private AccountManager acm;
		private byte[] oldSalt;

		CreatePwdTask(Account account, AccountManager accountManager,
				byte[] oldSalt) {
			this.account = account;
			acm = accountManager;
			this.oldSalt = oldSalt != null ? oldSalt.clone() : null;
		}

		@Override
		protected Exception doInBackground(Void... params) {
			// Create new PWD and send to server.
			ClientKeyHelper.clearPrivateKeyData(account, acm);

			Exception ex = null;
			boolean saved = false;
			// Remove server data
			try {
				if (isCancelled()) {
					return null;
				}
				ClientKeyHelper.getOrCreatePrivateKey(account, acm);

				if (isCancelled()) {
					return null;
				}
				// Save Salt
				String salt = ClientKeyHelper.getSalt(account, acm);
				String pwdCheckStr = ClientKeyHelper.getPwdCheck(account, acm);
				if (salt != null && !isCancelled() && pwdCheckStr != null) {
					saved = savePwdSalt(salt, pwdCheckStr);
				}
			} catch (NetworkErrorException e) {
				LogHelper.logI(TAG, "Failed to authenticate", e);
				ex = e;
			} catch (ServerException e) {
				LogHelper.logWCause(TAG,
						"Failed to create password due to a server error", e);
				ex = e;
			} catch (IOException e) {
				LogHelper.logE(TAG, "Failed to create key", e);
				ex = e;
			} catch (AuthenticationException e) {
				LogHelper.logWCause(TAG, "Authentication failed", e);
				ex = e;
			} catch (OperationCanceledException e) {
				LogHelper.logI(TAG, "Operation canceled", e);
			} catch (InvalidKeyException e) {
				LogHelper.logW(TAG, "Create key failed due to an invalid Key.",
						e);
				ex = e;
			}
			if (!saved) {
				ClientKeyHelper.clearPrivateKeyData(account, acm);
			}

			return ex;

		}

		private boolean savePwdSalt(String salt, String pwdCheckStr)
				throws OperationCanceledException, ServerException,
				NetworkErrorException, AuthenticationException {
			boolean retry;
			int retrycount = 1;
			boolean saved = false;
			String authToken = NetworkUtilities.blockingGetAuthToken(acm,
					account, null);
			if (authToken != null) {
				do {
					retry = false;
					try {
						boolean saltSaved = NetworkUtilities.savePwdSalt(
								CreatePwdProgressDialog.this.getActivity(),
								account.name, authToken, salt,
								Base64.encodeToString(oldSalt, Base64.DEFAULT),
								true, pwdCheckStr);
						if (saltSaved) {
							ClientKeyHelper.setSaltSaved(account, acm);
							saved = true;
						}
					} catch (AuthenticationException e) {
						LogHelper
								.logDCause(
										TAG,
										"Authentification failed, trying to get new Token",
										e);
						acm.invalidateAuthToken(Constants.ACCOUNT_TYPE,
								authToken);
						if (retrycount == 0) {
							throw e;
						}
						authToken = NetworkUtilities.blockingGetAuthToken(acm,
								account, null);
						if (authToken != null) {
							retry = true;
							retrycount--;
						} else {
							throw e;
						}
					}
				} while (retry);
			}
			return saved;
		}

		@Override
		protected void onPostExecute(final Exception exception) {
			super.onPostExecute(exception);
			if (exception == null) {
				success = true;
				canceled = false;
				taskError = null;
			} else {
				success = false;
				canceled = false;
				taskError = CreatePwdError.KEY_GEN_FAILED;
				if (exception instanceof NetworkErrorException) {
					taskError = CreatePwdError.NETWORK_ERROR;
				} else if (exception instanceof ServerException) {
					taskError = CreatePwdError.SERVER_ERROR;
				}
			}
			deliverResult();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			canceled = true;
			taskError = null;
			success = false;
			deliverResult();
		}
	}

	/**
	 * Represents an asynchronous task used to recreate a Key based on a
	 * Password
	 */
	private class RecreateKeyTask extends AsyncTask<Void, Void, Exception> {

		private final Account account;
		private final AccountManager acm;
		private byte[] currSalt;
		private byte[] pwdCheck;
		private final String pwd;

		RecreateKeyTask(Account account, AccountManager accountManager,
				byte[] currSalt, byte[] pwdCheck, String pwd) {
			this.account = account;
			acm = accountManager;
			this.pwdCheck = pwdCheck != null ? pwdCheck.clone() : null;
			this.pwd = pwd;
			this.currSalt = currSalt != null ? currSalt.clone() : null;
		}

		@Override
		protected Exception doInBackground(Void... params) {
			Exception ex = null;
			// Update CurrSalt because in the meantime the salt could have
			// changed from another client.
			updateCurrSalt();
			if (isCancelled()) {
				return null;
			}
			try {
				ClientKeyHelper.createKey(account, acm, pwd, currSalt, true,
						pwdCheck);
			} catch (UnsupportedEncodingException e) {
				LogHelper.logE(TAG, "Failed to Recreate Key", e);
				ex = e;
			} catch (InvalidKeyException e) {
				LogHelper.logI(TAG,
						"Recreate key failed due to an invalid password.", e);
				ex = e;
			}
			return ex;
		}

		private void updateCurrSalt() {
			String authtoken = null;
			try {
				authtoken = NetworkUtilities.blockingGetAuthToken(acm, account,
						getActivity());
				if (authtoken != null) {
					byte[] newKeySaltAndKeyCheck = NetworkUtilities.getKeySalt(
							CreatePwdProgressDialog.this.getActivity(),
							account.name, authtoken);
					if (newKeySaltAndKeyCheck != null) {
						Pair<byte[], byte[]> saltPwd = ClientKeyHelper
								.splitSaltPwdCheck(newKeySaltAndKeyCheck);
						currSalt = saltPwd.left;
						pwdCheck = saltPwd.right;
					}
				}
			} catch (AuthenticationException ex) {
				acm.invalidateAuthToken(Constants.ACCOUNT_TYPE, authtoken);
				LogHelper.logI(TAG,
						"Authentification failed, Don't update current salt.",
						ex);
			} catch (OperationCanceledException e) {
				LogHelper
						.logI(TAG,
								"Authentification canceled, couldn't update current salt.",
								e);
			} catch (NetworkErrorException e) {
				LogHelper.logI(TAG, "Could not update current salt.", e);
			} catch (ServerException e) {
				LogHelper.logI(TAG, "Could not update current salt.", e);
			}
		}

		@Override
		protected void onPostExecute(final Exception exception) {
			super.onPostExecute(exception);
			if (exception == null) {
				success = true;
				canceled = false;
				taskError = null;
			} else {
				success = false;
				canceled = false;
				taskError = CreatePwdError.KEY_VALIDATION_FAILED;
			}
			deliverResult();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			success = false;
			canceled = true;
			taskError = null;
			deliverResult();
		}
	}
}
