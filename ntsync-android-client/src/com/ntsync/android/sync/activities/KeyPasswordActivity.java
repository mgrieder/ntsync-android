
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.CreatePwdProgressDialog.CreatePwdError;
import com.ntsync.android.sync.client.ClientKeyHelper;
import com.ntsync.android.sync.platform.SystemHelper;
import com.ntsync.android.sync.shared.Constants;
import com.ntsync.android.sync.shared.UIHelper;
import com.ntsync.shared.Pair;

/**
 * Activity which displays key password input fields
 */
public class KeyPasswordActivity extends FragmentActivity implements
		OnClickListener, CreatePwdProgressDialog.CreatePwdDialogListener {

	/** The Intent extra to store username. */
	public static final String PARAM_USERNAME = "username";

	/** The Intent extra to store password salt. */
	public static final String PARAM_SALT = "salt";

	/** The Intent extra to store password salt. */
	public static final String PARAM_CHECK = "pwdcheck";

	/** The tag used to log to adb console. */
	private static final String TAG = "KeyPasswordActivity";

	private AutoCompleteTextView[] mPasswordEdit;

	private String mUsername;

	private TextView mMessage;

	private byte[] pwdSalt;

	private byte[] pwdCheck;

	private TextView msgNewKey;

	public static Intent createKeyPasswortActivity(Context context,
			Account account, final String authToken, byte[] saltPwdCheck) {
		Pair<byte[], byte[]> saltPwd = ClientKeyHelper
				.splitSaltPwdCheck(saltPwdCheck);

		// Nein, Passwort abfragen und Key erzeugen.
		final Intent intent = new Intent(context, KeyPasswordActivity.class);
		intent.putExtra(KeyPasswordActivity.PARAM_USERNAME, account.name);
		intent.putExtra(KeyPasswordActivity.PARAM_SALT, saltPwd.left);
		intent.putExtra(KeyPasswordActivity.PARAM_CHECK, saltPwd.right);
		return intent;
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Check if Account is available
		AccountManager acm = AccountManager.get(this);
		boolean found = false;
		for (Account accounts : acm.getAccounts()) {
			if (Constants.ACCOUNT_TYPE.equals(accounts.type)
					&& TextUtils.equals(accounts.name, mUsername)) {
				found = true;
			}
		}
		if (!found) {
			clearNotification(mUsername);
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		SystemHelper.initSystem(this);

		Log.i(TAG, "loading data from Intent");
		final Intent intent = getIntent();
		mUsername = intent.getStringExtra(PARAM_USERNAME);
		pwdSalt = intent.getByteArrayExtra(PARAM_SALT);
		pwdCheck = intent.getByteArrayExtra(PARAM_CHECK);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.keypassword_activity);
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
				R.drawable.key);
		mMessage = (TextView) findViewById(R.id.message_bottom);

		mPasswordEdit = new AutoCompleteTextView[5];
		mPasswordEdit[0] = (AutoCompleteTextView) findViewById(R.id.pwd1_edit);
		mPasswordEdit[1] = (AutoCompleteTextView) findViewById(R.id.pwd2_edit);
		mPasswordEdit[2] = (AutoCompleteTextView) findViewById(R.id.pwd3_edit);
		mPasswordEdit[3] = (AutoCompleteTextView) findViewById(R.id.pwd4_edit);
		mPasswordEdit[4] = (AutoCompleteTextView) findViewById(R.id.pwd5_edit);
		for (AutoCompleteTextView textView : mPasswordEdit) {
			textView.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
					| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}

		if (pwdSalt == null || pwdSalt.length != ClientKeyHelper.SALT_LENGHT
				|| pwdCheck == null) {
			// disable password input
			for (AutoCompleteTextView textView : mPasswordEdit) {
				if (textView != null) {
					textView.setEnabled(false);
				}
			}
		}
		msgNewKey = (TextView) findViewById(R.id.message_newkey);
		SpannableString newKeyText = SpannableString
				.valueOf(getText(R.string.keypwd_activity_newkey_label));

		newKeyText.setSpan(new InternalURLSpan(this), 0, newKeyText.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		msgNewKey.setText(newKeyText, BufferType.SPANNABLE);
		msgNewKey.setMovementMethod(LinkMovementMethod.getInstance());
	}

	/**
	 * Handles onClick event on the Submit button.
	 * 
	 * @param view
	 *            The Submit button for which this method is invoked
	 */
	public void savePwd(View view) {
		StringBuilder pwd = new StringBuilder();
		for (AutoCompleteTextView textView : mPasswordEdit) {
			String textVal = textView.getText().toString();
			if (TextUtils.isEmpty(textVal)) {
				mMessage.setText(R.string.keypwd_activity_pwd_missing);
				break;
			}
			if (pwd.length() > 0) {
				pwd.append(' ');
			}
			pwd.append(textVal.replace(" ", ""));
		}

		if (pwd.length() > 0) {
			// Generate Key
			CreatePwdProgressDialog progressDialog = CreatePwdProgressDialog
					.newInstance(mUsername, pwd.toString(), pwdSalt, pwdCheck);
			progressDialog.show(getSupportFragmentManager(), "ProgressDialog");
		}
	}

	/**
	 * Called when KeyPassword was set.
	 * 
	 * @param result
	 *            the confirmCredentials result.
	 */
	private void finishSetKeyPassword() {
		Log.i(TAG, "finishSetKeyPassword()");
		final Intent intent = new Intent();
		intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
		intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		setResult(RESULT_OK, intent);

		Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
		ContentResolver.requestSync(account, Constants.CONTACT_AUTHORITY,
				new Bundle());

		// Remove notification
		clearNotification(account.name);
		finish();
	}

	private void clearNotification(String accountName) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(accountName, Constants.NOTIF_MISSING_KEY);
	}

	public void onClick(View v) {
		msgNewKey.setEnabled(false);

		// Dialog anzeigen, if yes then
		ResetPwdDialogFragment resetPwdDialog = ResetPwdDialogFragment
				.newInstance(mUsername, pwdSalt);
		resetPwdDialog.show(getSupportFragmentManager(), "ResetPwdDialog");
	}

	@Override
	protected void onResumeFragments() {
		super.onResumeFragments();
		msgNewKey.setEnabled(true);
	}

	public static class ResetPwdDialogFragment extends DialogFragment {

		public static ResetPwdDialogFragment newInstance(String userName,
				byte[] pwdSalt) {
			ResetPwdDialogFragment dlg = new ResetPwdDialogFragment();

			Bundle args = new Bundle();
			args.putString(KeyPasswordActivity.PARAM_USERNAME, userName);
			args.putByteArray(KeyPasswordActivity.PARAM_SALT, pwdSalt);

			dlg.setArguments(args);

			return dlg;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.keypwd_activity_resetpwd_message);
			builder.setTitle(R.string.keypwd_activity_resetpwd_title);
			builder.setPositiveButton(R.string.ok_button_label,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							showProgress();
						}
					}).setNegativeButton(R.string.cancel_button_label, null);

			return builder.create();
		}

		private void showProgress() {
			Bundle args = getArguments();
			CreatePwdProgressDialog progressDialog = CreatePwdProgressDialog
					.newInstance(
							args.getString(KeyPasswordActivity.PARAM_USERNAME),
							args.getByteArray(KeyPasswordActivity.PARAM_SALT));

			progressDialog.show(getActivity().getSupportFragmentManager(),
					"ProgressDialog");
		}
	}

	public void onCreateEnd(boolean success, boolean cancel,
			CreatePwdError error) {
		mMessage.setText(null);
		if (success) {
			finishSetKeyPassword();
		} else {
			if (!cancel) {
				int msg;
				switch (error) {
				case NETWORK_ERROR:
					msg = R.string.keypwd_activity_networkerror;
					break;
				case SERVER_ERROR:
					msg = R.string.keypwd_activity_servererror;
					break;
				case KEY_VALIDATION_FAILED:
					msg = R.string.keypwd_activity_key_validation_failed;
					break;
				default:
					msg = R.string.keypwd_activity_keygen_failed;
					break;
				}

				mMessage.setText(getText(msg));
				//Make sure Message is visible
				
				ScrollView scrollView = (ScrollView) findViewById(R.id.scrollview);
				UIHelper.scrollToDeepChild(scrollView, mMessage);				
			}
		}
	}

}
