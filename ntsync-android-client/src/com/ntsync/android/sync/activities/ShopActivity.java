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

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.MessageDialog.ConfirmationDialogClosedListener;
import com.ntsync.android.sync.activities.VerifyPaymentProgressDialog.VerifyPaymentDialogListener;
import com.ntsync.android.sync.client.NetworkUtilities;
import com.ntsync.android.sync.client.ServerException;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.SyncUtils;
import com.ntsync.android.sync.shared.SyncUtils.PaymentData;
import com.ntsync.shared.PayPalConfirmationResult;
import com.ntsync.shared.Price;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;

/**
 * List all possible subscriptions for an account.
 */
public class ShopActivity extends AbstractFragmentActivity implements
		LoaderCallbacks<List<Price>>, VerifyPaymentDialogListener,
		ConfirmationDialogClosedListener {

	private static final String PAYPAL_RELEASE_CLIENTID = "AeaophDnRMpGTOjy0YeIH5IgnJhZidPmHaZC3f96Yi9UFKgQkPBzoKgamZux";

	private static final String PAYPAL_SANDBOX_CLIENTID = "ATxNXxD_Q96kZ3OqgFMBKQqxn3HBLpBBOONpjs4sZru9X_cg5zy3wVmYBykl";

	private static final String TAG = "ShopActivity";

	public static final String PARM_ACCOUNT_NAME = "accountName";

	/** Used to display a Message (eg Result of Payment-Verfication) */
	public static final String PARM_MSG = "resultMsg";

	private static final String INSTKEY_SELECTED_PRICE = "com.ntsync.selectedprice";

	private static final int LOADID_PRICES = 0;

	/**
	 * After 2.h (after 3h a Price will be rejected from the server), and
	 * Price-List should be ignored.
	 */
	private static final long FORCE_PRICE_LOAD_TIME = 150 * 60 * 1000;

	private String accountName;

	private AppListAdapter adapter;

	private UUID selectedPriceId = null;

	private PaymentResult paymentResult = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.shop_items);

		this.accountName = getIntent().getStringExtra(PARM_ACCOUNT_NAME);
		if (accountName == null || accountName.length() == 0) {
			finish();
			return;
		}

		adapter = new AppListAdapter(this);
		getListView().setAdapter(adapter);

		// Start PayPal
		Intent intent = new Intent(this, PayPalService.class);
		if (Constants.USE_RELEASE_CONFIG) {
			intent.putExtra(PaymentActivity.EXTRA_CLIENT_ID,
					PAYPAL_RELEASE_CLIENTID);
		} else {
			// sandbox: use PaymentActivity.ENVIRONMENT_SANDBOX
			intent.putExtra(PaymentActivity.EXTRA_PAYPAL_ENVIRONMENT,
					PaymentActivity.ENVIRONMENT_SANDBOX);
			intent.putExtra(PaymentActivity.EXTRA_CLIENT_ID,
					PAYPAL_SANDBOX_CLIENTID);
		}
		// Direct Credit is only possible with a UK/Canada PayPal-Account
		intent.putExtra(PaymentActivity.EXTRA_SKIP_CREDIT_CARD, true);

		startService(intent);

		// if there is a Payment open, don't let user go ahead
		if (SyncUtils.hasPaymentData(this)) {

			if (SyncUtils.isNetworkConnected(this)) {
				// if network is available -> ask if payment should be disabled
				MessageDialog.showConfirmation(
						getText(R.string.shop_activity_ensurepaymentcancel),
						getText(R.string.shop_activity_stopverification),
						getText(R.string.shop_activity_stopbutton), this);
			} else {
				// Network should be enabled to verify payment.
				MessageDialog.showAndClose(
						R.string.shop_activity_network_forverification, this);
			}
		}
	}

	public void messageDialogClosed(CharSequence dlgTitle,
			boolean positiveBtnPressed) {
		if (positiveBtnPressed) {
			// Delete all PaymentData
			AccountManager acm = AccountManager.get(this);
			Account[] accounts = acm.getAccountsByType(Constants.ACCOUNT_TYPE);
			for (Account account : accounts) {
				PaymentData paymentData = SyncUtils.getPayment(account, acm);
				if (paymentData != null) {
					SyncUtils.savePayment(account, acm, null, null);
					break;
				}
			}
		} else {
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(INSTKEY_SELECTED_PRICE,
				selectedPriceId != null ? selectedPriceId.toString() : null);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		String selectedPriceIdStr = savedInstanceState
				.getString(INSTKEY_SELECTED_PRICE);
		selectedPriceId = selectedPriceIdStr != null ? UUID
				.fromString(selectedPriceIdStr) : null;
	}

	@Override
	protected void onDestroy() {
		stopService(new Intent(this, PayPalService.class));
		super.onDestroy();
	}

	private ListView getListView() {
		return (ListView) findViewById(R.id.shopItemList);
	}

	public Loader<List<Price>> onCreateLoader(int id, Bundle args) {
		return new PriceListLoader(this, accountName);

	}

	public void onLoadFinished(Loader<List<Price>> loader, List<Price> data) {
		adapter.setData(data);
		if (data != null && data.isEmpty()) {
			// Reload-Btn anzeigen when loading failed.
			View progressBar = findViewById(R.id.progressBar);
			progressBar.setVisibility(View.GONE);
			View reloadBtn = findViewById(R.id.reloadBtn);
			reloadBtn.setVisibility(View.VISIBLE);
			getListView().setEmptyView(reloadBtn);
		}

		if (data == null) {
			this.finish();
		}
	}

	public void onLoaderReset(Loader<List<Price>> loader) {
		adapter.setData(null);
	}

	public void handleReload(View view) {
		getSupportLoaderManager().restartLoader(LOADID_PRICES, null, this);
		View progressBar = findViewById(R.id.progressBar);
		progressBar.setVisibility(View.VISIBLE);
		View reloadBtn = findViewById(R.id.reloadBtn);
		reloadBtn.setVisibility(View.GONE);
		getListView().setEmptyView(progressBar);
	}

	private String getOrderText(Price price) {
		CharSequence durText = getResources().getQuantityString(
				R.plurals.shop_activity_duration, price.getDuration(),
				price.getDuration());
		return String.format(getText(R.string.shop_order_name).toString(),
				durText);
	}

	public void handleBuy(View view) {
		int position = getListView()
				.getPositionForView((View) view.getParent());
		Price price = (Price) getListView().getItemAtPosition(position);
		if (price != null) {
			String description = getOrderText(price);
			PayPalPayment payment = new PayPalPayment(price.getPrice(),
					price.getCurrency(), description);

			Intent intent = new Intent(this, PaymentActivity.class);

			if (Constants.USE_RELEASE_CONFIG) {
				intent.putExtra(PaymentActivity.EXTRA_CLIENT_ID,
						PAYPAL_RELEASE_CLIENTID);
				intent.putExtra(PaymentActivity.EXTRA_RECEIVER_EMAIL,
						"markus@grieder.me");
			} else {
				// sandbox: use PaymentActivity.ENVIRONMENT_SANDBOX
				intent.putExtra(PaymentActivity.EXTRA_PAYPAL_ENVIRONMENT,
						PaymentActivity.ENVIRONMENT_SANDBOX);
				intent.putExtra(PaymentActivity.EXTRA_CLIENT_ID,
						PAYPAL_SANDBOX_CLIENTID);
				intent.putExtra(PaymentActivity.EXTRA_RECEIVER_EMAIL,
						"markus-facilitator@grieder.me");
			}
			// Direct Credit is only possible with a UK/Canada PayPal-Account
			intent.putExtra(PaymentActivity.EXTRA_SKIP_CREDIT_CARD, true);

			// Provide a payerId that uniquely identifies a user within the
			// scope of your system, such as an email address or user ID.
			intent.putExtra(PaymentActivity.EXTRA_PAYER_ID, accountName);
			intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment);

			selectedPriceId = price.getPriceId();

			startActivityForResult(intent, 0);
		}
	}

	public void onVerifyComplete(PayPalConfirmationResult result) {
		boolean removePayment = false;
		AccountManager acm = AccountManager.get(this);
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);

		if (result != null) {
			LogHelper.logI(TAG, "Payment verified. Result:" + result);
			switch (result) {
			case SUCCESS:
				removePayment = true;
				onPaymentSuccess(account);
				break;
			case INVALID_PRICE:
				removePayment = true;
				MessageDialog.show(R.string.shop_activity_invalidprice, this);
				break;
			case INVALID_SYNTAX:
				removePayment = true;
				MessageDialog.show(R.string.shop_activity_invalidsyntax, this);
				break;
			case CANCELED:
				removePayment = true;
				MessageDialog
						.show(R.string.shop_activity_paymentcanceled, this);
				break;
			case NOT_APPROVED:
			case SALE_NOT_COMPLETED:
				MessageDialog.show(R.string.shop_activity_notapproved, this);
				break;
			case VERIFIER_ERROR:
				MessageDialog.showAndClose(
						R.string.shop_activity_verifiererror, this);
				break;
			case ALREADY_PROCESSED:
				removePayment = true;
				MessageDialog.show(R.string.shop_activity_paymentalreadyused,
						this);
				break;
			case UNKNOWN_PAYMENT:
				MessageDialog.showAndClose(
						R.string.shop_activity_verificationfailed, this);
				// Try to validate it one more time, later.
				break;
			case AUTHENTICATION_FAILED:
				MessageDialog.showAndClose(R.string.shop_activity_authfailed,
						this);
				break;
			case NETWORK_ERROR:
				MessageDialog.showAndClose(R.string.shop_activity_networkerror,
						this);
				break;

			default:
				removePayment = true;
				LogHelper.logE(TAG, "Unknown PaymentVerificationResult:"
						+ result, null);
				// Unknown State
				MessageDialog.showAndClose(
						R.string.shop_activity_verificationfailed, this);
				break;
			}
		} else {
			finish();
		}

		if (removePayment) {
			SyncUtils.savePayment(account, acm, null, null);
		}
		SyncUtils.stopPaymentVerification();
	}

	private void onPaymentSuccess(Account account) {
		MessageDialog.showAndClose(R.string.shop_activity_paymentsuccess, this);
		// Perform sync
		Bundle extras = new Bundle();
		extras.putBoolean(Constants.PARAM_GETRESTRICTIONS, true);
		ContentResolver.requestSync(account, Constants.CONTACT_AUTHORITY,
				extras);
	}

	@Override
	protected void onResumeFragments() {
		super.onResumeFragments();
		// Process a PaymentResult from onActivityResult
		if (paymentResult != null) {
			int resultCode = paymentResult.resultCode;
			PaymentConfirmation confirm = paymentResult.confirmation;
			paymentResult = null;
			if (resultCode == Activity.RESULT_OK && confirm != null) {
				if (selectedPriceId == null) {
					MessageDialog.show(R.string.shop_activity_missingprice,
							this);
					return;
				}

				JSONObject paymentJson = confirm.toJSONObject();
				// Save Payment, so that payment can be verified later.
				Account account = new Account(accountName,
						Constants.ACCOUNT_TYPE);
				AccountManager accountManager = AccountManager.get(this);
				SyncUtils.savePayment(account, accountManager, paymentJson,
						selectedPriceId);

				SyncUtils.startPaymentVerification();
				// Start Timer to verify Payment if Verification could not be
				// done now.
				PaymentVerificationService
						.startVerificationTimer(getApplicationContext());

				// Send Confirmation to server
				boolean verifStarted = false;
				try {
					String jsonData = paymentJson.toString(1);
					VerifyPaymentProgressDialog progressDialog = VerifyPaymentProgressDialog
							.newInstance(selectedPriceId, jsonData, accountName);
					progressDialog.show(this.getSupportFragmentManager(),
							"VerifyPaymentProgressDialog");
					if (Log.isLoggable(TAG, Log.DEBUG)) {
						Log.d(TAG, "PaymentConfirmation: " + jsonData);
					}
					verifStarted = true;
				} catch (JSONException e) {
					MessageDialog.show(R.string.shop_activity_invalidsyntax,
							this);
					Log.e(TAG, "Failed to convert Payment to JSON.", e);
					SyncUtils.savePayment(account, accountManager, null, null);
				} finally {
					if (!verifStarted) {
						SyncUtils.stopPaymentVerification();
					}
				}
			} else if (resultCode == Activity.RESULT_CANCELED) {
				Log.i(TAG, "The user canceled the payment-flow");
			} else if (resultCode == PaymentActivity.RESULT_PAYMENT_INVALID) {
				MessageDialog.show(R.string.shop_activity_invalidpayment, this);
				Log.i(TAG, "An invalid payment was submitted.");
			} else {
				MessageDialog.show(R.string.shop_activity_invalidpayment, this);
				Log.e(TAG, "PaymentResult is unknown. Result:" + resultCode
						+ " Confirmation:" + confirm);
			}
		}
		getSupportLoaderManager().initLoader(LOADID_PRICES, null, this);
		View progressBar = findViewById(R.id.progressBar);
		View reloadBtn = findViewById(R.id.reloadBtn);
		reloadBtn.setVisibility(View.GONE);
		progressBar.setVisibility(View.VISIBLE);
		getListView().setEmptyView(progressBar);

		// Show a Message from a delayed Verification
		String msg = getIntent().getStringExtra(PARM_MSG);
		if (msg != null) {
			MessageDialog.show(msg, this);
			getIntent().removeExtra(PARM_MSG);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		// Postpone a Payment-Result to onResumeFragment, back from payment
		// returns null-data.
		PaymentConfirmation confirm = null;
		if (data != null) {
			confirm = data
					.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
		}
		paymentResult = new PaymentResult(resultCode, confirm);
	}

	/**
	 * A custom Loader that loads all of the installed applications.
	 */
	public static class PriceListLoader extends AsyncTaskLoader<List<Price>> {

		List<Price> priceList;
		private final AccountManager accountManager;
		private String accountName;

		private long loaded = -1;

		public PriceListLoader(Context context, String accountName) {
			super(context);
			this.accountName = accountName;

			accountManager = AccountManager.get(context);
		}

		@Override
		public List<Price> loadInBackground() {
			String loadAccountName = accountName;
			Account[] accounts = accountManager
					.getAccountsByType(Constants.ACCOUNT_TYPE);
			Account account = null;
			if (loadAccountName != null) {
				for (Account currAc : accounts) {
					if (loadAccountName.equals(currAc.name)) {
						account = currAc;
						break;
					}
				}
			}
			if (account == null) {
				// Account not found
				return null;
			}

			String currIsoCode;
			try {
				Currency currency = Currency.getInstance(Locale.getDefault());
				currIsoCode = currency.getCurrencyCode();
			} catch (IllegalArgumentException e) {
				LogHelper
						.logWCause(TAG, "Invalid Default Currency. Use EUR", e);
				currIsoCode = "EUR";
			}

			String authtoken = null;
			List<Price> prices;
			try {
				Context ctx = getContext();
				authtoken = NetworkUtilities.blockingGetAuthToken(
						accountManager, account,
						ctx instanceof Activity ? (Activity) ctx : null);
				if (authtoken == null) {
					prices = Collections.emptyList();
				} else {
					prices = NetworkUtilities.getPrices(ctx, account,
							authtoken, accountManager, currIsoCode);
				}
			} catch (AuthenticationException e) {
				LogHelper.logI(TAG, "Authentification failed. Provide Reload",
						e);
				// Give Reload-Option
				prices = Collections.emptyList();
			} catch (OperationCanceledException e) {
				LogHelper.logD(TAG, "Authentification canceled", e);
				// Give Reload-Option
				prices = Collections.emptyList();
			} catch (NetworkErrorException e) {
				LogHelper.logI(TAG, "loading Prices failed. Provide Reload", e);
				// Give Reload-Option
				prices = Collections.emptyList();
			} catch (ServerException e) {
				LogHelper.logI(TAG, "loading Prices failed. Provide Reload", e);
				// Give Reload-Option
				prices = Collections.emptyList();
			}
			return prices;
		}

		/**
		 * Called when there is new data to deliver to the client. The super
		 * class will take care of delivering it; the implementation here just
		 * adds a little more logic.
		 */
		@Override
		public void deliverResult(List<Price> priceData) {
			List<Price> oldPriceList = priceList;
			priceList = priceData;
			loaded = System.currentTimeMillis();

			if (oldPriceList == null) {
				// Always deliver result when List is not yet loaded, otherwise
				// progress bar will not be removed
				super.deliverResult(priceList);
				return;
			}

			if (isStarted()
					&& (priceData == null || !(priceData.equals(oldPriceList)))) {
				super.deliverResult(priceData);
			}
		}

		@Override
		protected void onStartLoading() {
			boolean reset = false;
			if (priceList != null) {
				if (loaded > 0
						&& System.currentTimeMillis() > loaded
								+ FORCE_PRICE_LOAD_TIME) {
					priceList = Collections.emptyList();
					reset = true;
				} else {
					deliverResult(priceList);
				}
			}
			if (reset) {
				reset();
			}
			forceLoad();
		}

		@Override
		protected void onStopLoading() {
			cancelLoad();
		}

		@Override
		protected void onReset() {
			super.onReset();

			onStopLoading();
			priceList = null;
		}
	}

	public static class AppListAdapter extends ArrayAdapter<Price> {
		private final LayoutInflater mInflater;

		public AppListAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_2);
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public void setData(List<Price> data) {
			clear();
			if (data != null) {
				// addAll need Api 11: use workaround
				setNotifyOnChange(false);
				for (Price price : data) {
					add(price);
				}
				setNotifyOnChange(true);
				notifyDataSetChanged();
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;

			if (convertView == null) {
				view = mInflater.inflate(R.layout.shop_listitem_row, parent,
						false);
			} else {
				view = convertView;
			}

			Price item = getItem(position);

			try {
				NumberFormat format = NumberFormat.getCurrencyInstance();
				format.setCurrency(Currency.getInstance(item.getCurrency()));
				((TextView) view.findViewById(R.id.price)).setText(format
						.format(item.getPrice()));
			} catch (IllegalArgumentException ex) {
				LogHelper.logWCause(TAG,
						"Could not format Price with Currency", ex);
				((TextView) view.findViewById(R.id.price)).setText(item
						.getPrice() + " " + item.getCurrency());
			}

			CharSequence durText = view.getResources().getQuantityString(
					R.plurals.shop_activity_duration, item.getDuration(),
					item.getDuration());
			((TextView) view.findViewById(R.id.duration)).setText(durText);

			CharSequence extraTxt = null;
			if (item.getDiscount() > 0) {
				extraTxt = String.format(
						view.getResources()
								.getText(R.string.shop_activity_discount)
								.toString(), item.getDiscount());
			} else if (item.isHit()) {
				extraTxt = view.getResources().getText(
						R.string.shop_activity_bigseller);
			}
			((TextView) view.findViewById(R.id.percent)).setText(extraTxt);

			return view;
		}
	}

	private static class PaymentResult {
		private final int resultCode;
		private final PaymentConfirmation confirmation;

		public PaymentResult(int resultCode, PaymentConfirmation confirmation) {
			super();
			this.resultCode = resultCode;
			this.confirmation = confirmation;
		}

	}
}
