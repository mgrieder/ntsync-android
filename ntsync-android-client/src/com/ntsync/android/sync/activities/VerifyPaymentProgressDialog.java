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

import java.util.UUID;

import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.shared.PayPalConfirmationResult;

/**
 * Send the PayPalProofOfPayment to the server while a ProgressDialog is
 * visible.
 */
public class VerifyPaymentProgressDialog extends DialogFragment {

	/** The tag used to log to adb console. */
	private static final String TAG = "CreatePwdProgressDialog";

	private static final String PARAM_PRICEID = "priceId";

	private static final String PARAM_PROOFOFPAYMENT = "payment";

	private static final String PARAM_ACCOUNTNAME = "accountname";

	private AsyncTask<Void, Void, PayPalConfirmationResult> verifyTask = null;

	private PayPalConfirmationResult taskResult = null;

	/**
	 * Creates a Verify Progress Dialog
	 * 
	 * @param userName
	 * @param pwdSalt
	 * @param authtoken
	 * @return invisible Dialog
	 */
	public static VerifyPaymentProgressDialog newInstance(UUID selectedPriceId,
			String jsonProofOfPayment, String accountName) {
		VerifyPaymentProgressDialog dlg = new VerifyPaymentProgressDialog();
		Bundle args = new Bundle();
		args.putString(PARAM_PRICEID, selectedPriceId.toString());
		args.putString(PARAM_PROOFOFPAYMENT, jsonProofOfPayment);
		args.putString(PARAM_ACCOUNTNAME, accountName);
		dlg.setArguments(args);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		String priceId = args.getString(PARAM_PRICEID);
		String jsonProofOfPayment = args.getString(PARAM_PROOFOFPAYMENT);
		String accountName = args.getString(PARAM_ACCOUNTNAME);
		AccountManager acM = AccountManager.get(this.getActivity());
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);

		// Retain to keep Task during conf changes
		setRetainInstance(true);
		setCancelable(false);

		verifyTask = new VerifyPaymentTask(priceId, jsonProofOfPayment,
				account, acM);
		verifyTask.execute();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getText(R.string.shop_activity_verifyprogress));
		dialog.setIndeterminate(true);
		return dialog;
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if (verifyTask != null) {
			verifyTask.cancel(true);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (verifyTask != null && verifyTask.getStatus() == Status.FINISHED) {
			deliverResult();
		}
	}

	protected void deliverResult() {
		// deliver result otherwise in onResume, when killed task will be
		// started again.
		if (isResumed()) {
			dismiss();
			if (resultListener != null) {
				resultListener.onVerifyComplete(taskResult);
			}
		}
	}

	/**
	 * The activity that creates an instance of this dialog fragment must
	 * implement this interface in order to receive event callbacks.
	 */
	public interface VerifyPaymentDialogListener {
		/**
		 * 
		 * @param success
		 * @param cancel
		 * @param error
		 *            if success is false, contains an error
		 */
		void onVerifyComplete(PayPalConfirmationResult result);
	}

	/** Use this instance of the interface to deliver action events */
	private VerifyPaymentDialogListener resultListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			resultListener = (VerifyPaymentDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			ClassCastException ex = new ClassCastException(activity.toString()
					+ " must implement VerifyPaymentDialogListener");
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Represents an asynchronous task used to create Password
	 */
	private class VerifyPaymentTask extends
			AsyncTask<Void, Void, PayPalConfirmationResult> {

		private final String priceId;
		private final String jsonProofOfPayment;
		private VerifyPaymentProgressDialog dialog;
		private AccountManager acm;
		private Account account;

		public VerifyPaymentTask(String priceId, String jsonProofOfPayment,
				Account account, AccountManager acm) {
			this.priceId = priceId;
			this.jsonProofOfPayment = jsonProofOfPayment;
			this.dialog = VerifyPaymentProgressDialog.this;
			this.account = account;
			this.acm = acm;
		}

		@Override
		protected PayPalConfirmationResult doInBackground(Void... params) {
			PayPalConfirmationResult result = null;
			if (!isCancelled()) {
				try {
					String authtoken = NetworkUtilities.blockingGetAuthToken(
							acm, account, getActivity());
					if (authtoken == null) {
						return PayPalConfirmationResult.AUTHENTICATION_FAILED;
					}

					result = NetworkUtilities.verifyPayPalPayment(
							dialog.getActivity(), account, priceId,
							jsonProofOfPayment, authtoken, acm);
				} catch (NetworkErrorException e) {
					result = PayPalConfirmationResult.NETWORK_ERROR;
					LogHelper.logWCause(TAG, "Verifying PayPalPayment failed.",
							e);
				} catch (AuthenticationException e) {
					result = PayPalConfirmationResult.AUTHENTICATION_FAILED;
					LogHelper.logWCause(TAG, "Verifying PayPalPayment failed.",
							e);
				} catch (OperationCanceledException e) {
					result = PayPalConfirmationResult.AUTHENTICATION_FAILED;
					LogHelper.logD(TAG, "Verifying canceled.", e);
				} catch (ServerException e) {
					result = PayPalConfirmationResult.VERIFIER_ERROR;
					LogHelper.logWCause(TAG, "Verifying PayPalPayment failed.",
							e);
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(final PayPalConfirmationResult result) {
			super.onPostExecute(result);
			taskResult = result;
			deliverResult();
		}

		@Override
		protected void onCancelled(PayPalConfirmationResult result) {
			taskResult = result;
			deliverResult();
		}
	}
}