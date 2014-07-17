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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.SyncUtils;
import com.ntsync.android.sync.shared.SyncUtils.PaymentData;
import com.ntsync.shared.PayPalConfirmationResult;

/**
 * PaymentVerification Service, which verifies a Payment with our service.
 */
public class PaymentVerificationService extends IntentService {

	/**
	 * Time in Seconds for the Initial Delay to the start the Payment
	 * Verification. Delay: 5 min
	 */
	private static final int VERIFICATION_INITIAL_DELAY = 300;

	/**
	 * Interval in Seconds when we should try a new Payment Verification. Value:
	 * 40min
	 */
	private static final int VERIFICATION_INTERVAL = 2400;

	private static final String TAG = "PaymentVerificationService";

	private static final AtomicReference<ScheduledExecutorService> SCHEDULER_REF = new AtomicReference<ScheduledExecutorService>();

	/** Notify user about the pending Payment Verification after 3 days */
	private static final int TIMEOUT_PENDING_PAYMENT = 3 * 24 * 60 * 60 * 1000;

	public PaymentVerificationService() {
		super(TAG);
		SystemHelper.initSystem(this);
		this.setIntentRedelivery(true);
	}

	/**
	 * Starts PaymentData Verification Timer, if not already running and
	 * PaymentData is open to verify
	 * */
	public static void startVerificationTimer(final Context context) {
		if (SyncUtils.hasPaymentData(context)) {
			ScheduledExecutorService scheduler = SCHEDULER_REF.get();
			if (scheduler == null || scheduler.isShutdown()) {
				scheduler = Executors.newScheduledThreadPool(1);
				SCHEDULER_REF.set(scheduler);
			} else {
				// Timer aktiv
				return;
			}
			final ScheduledExecutorService sched = scheduler;

			final Runnable verifTimer = new Runnable() {
				public void run() {
					runVerifier(context, sched);
				}
			};
			// Verificate PaymentData every 1h for

			scheduler.scheduleAtFixedRate(verifTimer,
					VERIFICATION_INITIAL_DELAY, VERIFICATION_INTERVAL,
					TimeUnit.SECONDS);
		}
	}

	private static void runVerifier(final Context context,
			final ScheduledExecutorService sched) {
		SystemHelper.initSystem(context);
		// Check if PaymentData available -> when no cancel
		AccountManager acm = AccountManager.get(context);
		Account[] accounts = acm.getAccountsByType(Constants.ACCOUNT_TYPE);
		boolean foundPaymentData = false;
		for (Account account : accounts) {
			PaymentData paymentData = SyncUtils.getPayment(account, acm);
			if (paymentData != null) {
				if (SyncUtils.isPaymentVerificationStarted()) {
					return;
				}

				if (System.currentTimeMillis() > paymentData.paymentSaveDate
						+ TIMEOUT_PENDING_PAYMENT) {
					sendNotification(context, account,
							R.string.shop_activity_pendingverification,
							ShopActivity.class, false);
				}
				foundPaymentData = true;
				break;
			}
		}

		if (foundPaymentData) {
			// Check if user has to be notified

			// Start Service
			Intent verifService = new Intent(context,
					PaymentVerificationService.class);
			context.startService(verifService);
			LogHelper.logD(TAG, "Start PaymentVerificationService", null);
		} else {
			sched.shutdownNow();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		SystemHelper.initSystem(this, true);
	}

	@Override
	protected void onHandleIntent(Intent arg0) {
		if (SyncUtils.isNetworkConnected(this)) {
			checkForPaymentVerification(this);
		}
	}

	/**
	 * Checks for a pending PaymentVerification
	 */
	private void checkForPaymentVerification(Context context) {
		AccountManager acm = AccountManager.get(context);
		Account[] accounts = acm.getAccountsByType(Constants.ACCOUNT_TYPE);
		for (Account account : accounts) {
			// Don't verify is already a Verification is in Progress
			if (SyncUtils.isPaymentVerificationStarted()) {
				LogHelper
						.logD(TAG,
								"PaymentVerification skipped, because another verifcation is running",
								null);
				break;
			}

			PaymentData paymentData = SyncUtils.getPayment(account, acm);
			if (paymentData != null) {
				PayPalConfirmationResult result = null;
				try {
					LogHelper.logI(TAG,
							"PaymentVerification started for account "
									+ account.name);
					String authtoken = NetworkUtilities.blockingGetAuthToken(
							acm, account, null);
					if (authtoken == null) {
						continue;
					}
					result = NetworkUtilities.verifyPayPalPayment(context,
							account, paymentData.priceId.toString(),
							paymentData.paymentConfirmation.toString(),
							authtoken, acm);
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
				if (result != null) {
					processResult(context, acm, account, result);
				}
			}
		}
	}

	private void processResult(Context context, AccountManager acm,
			Account account, PayPalConfirmationResult result) {
		boolean removePayment = false;

		int msg = -1;
		Class<? extends Activity> activityClass = ShopActivity.class;

		LogHelper.logI(TAG, "PaymentVerification Result:  " + result);
		switch (result) {
		case SUCCESS:
			removePayment = true;
			msg = R.string.shop_activity_paymentsuccess;
			Bundle extras = new Bundle();
			extras.putBoolean(Constants.PARAM_GETRESTRICTIONS, true);
			ContentResolver.requestSync(account, Constants.CONTACT_AUTHORITY,
					extras);
			activityClass = ViewAccountsActivity.class;
			break;
		case INVALID_PRICE:
			removePayment = true;
			msg = R.string.shop_activity_invalidprice;
			break;
		case INVALID_SYNTAX:
			removePayment = true;
			msg = R.string.shop_activity_invalidsyntax;
			break;
		case CANCELED:
			removePayment = true;
			msg = R.string.shop_activity_paymentcanceled;
			break;
		case NOT_APPROVED:
		case SALE_NOT_COMPLETED:
		case VERIFIER_ERROR:
		case NETWORK_ERROR:
		case AUTHENTICATION_FAILED:
			// Don't show message -> try again later
			break;
		case ALREADY_PROCESSED:
			removePayment = true;
			break;
		case UNKNOWN_PAYMENT:
			removePayment = true;
			msg = R.string.shop_activity_verificationfailed;
			break;
		default:
			removePayment = true;
			LogHelper.logE(TAG, "Unknown PaymentVerificationResult:" + result,
					null);
			// Unknown State
			msg = R.string.shop_activity_verificationfailed;
			break;
		}

		if (removePayment) {
			SyncUtils.savePayment(account, acm, null, null);
		}
		if (msg > 0) {
			sendNotification(context, account, msg, activityClass, true);

		}
	}

	private static void sendNotification(Context context, Account account,
			int msg, Class<? extends Activity> activityClass, boolean showMsg) {
		NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		Intent viewIntent = new Intent(context, activityClass);
		if (ShopActivity.class.equals(activityClass)) {
			// Vollständige Meldung in der View anzeigen
			CharSequence msgText = context.getText(msg);
			if (showMsg) {
				// Show Full Message in ShopActivity
				viewIntent.putExtra(ShopActivity.PARM_MSG, String.format(
						context.getText(
								R.string.shop_activity_delayedverif_failed)
								.toString(), msgText));
			}
			viewIntent.putExtra(ShopActivity.PARM_ACCOUNT_NAME, account.name);
		}

		// Adds the back stack
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(activityClass);
		stackBuilder.addNextIntent(viewIntent);

		// Photo sync possible.
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				context)
				.setSmallIcon(Constants.NOTIF_ICON)
				.setContentTitle(context.getText(msg))
				.setContentText(account.name)
				.setAutoCancel(true)
				.setOnlyAlertOnce(true)
				.setContentIntent(
						stackBuilder.getPendingIntent(0,
								PendingIntent.FLAG_UPDATE_CURRENT));
		notificationManager.notify(Constants.NOTIF_PAYMENT_VERIFICATIONRESULT,
				builder.build());
	}
}