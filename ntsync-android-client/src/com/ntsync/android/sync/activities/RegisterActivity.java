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

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.activities.RegistrateProgressDialog.RegistrateDialogListener;
import com.ntsync.android.sync.activities.RegistrateProgressDialog.RegistrationError;
import com.ntsync.android.sync.shared.LogHelper;
import com.ntsync.android.sync.shared.UIHelper;
import com.ntsync.shared.UserRegistrationState;

/**
 * Allows to add a new account and login
 * 
 * @author markus
 */
public class RegisterActivity extends AbstractFragmentActivity implements
		RegistrateDialogListener {
	private static final String TAG = "RegisterActivity";

	public static final String PARAM_USERNAME = "username";

	/** Result-Param: Password as String */
	public static final String PARAM_PWD = "password";
	public static final String PARAM_EMAIL = "email";

	public RegisterActivity() {
		super(R.id.action_viewkey);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_register);

		TextView termsText = (TextView) findViewById(R.id.message_terms);
		termsText.setText(Html.fromHtml(getText(
				R.string.register_activity_terms_message).toString()));
		termsText.setMovementMethod(LinkMovementMethod.getInstance());
	}

	public void handleRegister(View view) {
		EditText passwordEdit = (EditText) findViewById(R.id.password_edit);
		EditText emailEdit = (EditText) findViewById(R.id.email_edit);
		EditText nameEdit = (EditText) findViewById(R.id.name_edit);

		boolean ok = true;

		String email = emailEdit.getText().toString().trim();
		String name = nameEdit.getText().toString().trim();
		String password = passwordEdit.getText().toString();

		View scrollToComp = null;
		// Validate
		if (TextUtils.isEmpty(password)) {
			passwordEdit
					.setError(getText(R.string.register_activity_password_missing));
			ok = false;
			scrollToComp = passwordEdit;
		} else if (password.length() < 6) {
			passwordEdit
					.setError(getText(R.string.register_activity_passwort_tooshort));
			ok = false;
			scrollToComp = passwordEdit;
		}
		if (TextUtils.isEmpty(name)) {
			nameEdit.setError(getText(R.string.register_activity_name_missing));
			ok = false;
			scrollToComp = nameEdit;
		}
		if (TextUtils.isEmpty(email)) {
			emailEdit
					.setError(getText(R.string.register_activity_email_missing));
			ok = false;
			scrollToComp = emailEdit;
		} else if (!email.contains("@") || !email.contains(".")) {
			emailEdit
					.setError(getText(R.string.register_activity_email_invalid));
			scrollToComp = emailEdit;
			ok = false;
		}

		CharSequence msgText = null;
		TextView textView = (TextView) findViewById(R.id.message_bottom);
		textView.setVisibility(TextUtils.isEmpty(msgText) ? View.INVISIBLE
				: View.VISIBLE);
		textView.setText(msgText);
		textView.setError(TextUtils.isEmpty(msgText) ? null
				: getText(R.string.register_activity_retry_message));

		// Send Registration
		if (ok) {
			RegistrateProgressDialog progressDialog = RegistrateProgressDialog
					.newInstance(email, password, name);
			progressDialog.show(this.getSupportFragmentManager(),
					"RegistrateProgressDialog");
		} else if (scrollToComp != null) {
			ScrollView scrollView = (ScrollView) findViewById(R.id.scrollview);
			UIHelper.scrollToDeepChild(scrollView, scrollToComp);
		}
	}

	public void onCreateEnd(UserRegistrationState state, boolean cancel,
			RegistrationError error, String email, byte[] srpPassword,
			String username) {

		boolean ok = false;
		TextView textView = (TextView) findViewById(R.id.message_bottom);
		CharSequence pwdErrorText = null;
		CharSequence resultMsg = null;
		CharSequence emailErrorText = null;

		if (state != null) {
			switch (state) {
			case EMAILSEND_FAILED:
			case SUCCESS:
				ok = true;
				break;
			case INVALID_PASSWORDDATA:
				pwdErrorText = getText(R.string.register_activity_password_missing);
				break;
			case EMAIL_INVALID:
				emailErrorText = getText(R.string.register_activity_email_invalid);
				break;
			case USERNAME_IN_USE:
				resultMsg = getText(R.string.register_activity_registrate_servererror);
				break;
			case USERNAME_INVALID:
				resultMsg = getText(R.string.register_activity_registrate_servererror);
				break;
			case EMAIL_ALREADY_REGISTRATED:
				resultMsg = getText(R.string.register_activity_email_inuse);
				break;
			case EMAIL_REJECTED:
				resultMsg = getText(R.string.register_activity_email_rejected);
				break;
			case TRY_LATER:
				resultMsg = getText(R.string.register_activity_try_later);
				break;
			default:
				LogHelper.logE(TAG, "Unkown Registration-Result: " + state,
						null);
				resultMsg = getText(R.string.register_activity_registrate_servererror);
				break;
			}

		} else if (!cancel && error != null) {
			int msgId;
			switch (error) {
			case NETWORK_ERROR:
				msgId = R.string.register_activity_registrate_networkerror;
				break;
			case SERVER_ERROR:
				msgId = R.string.register_activity_registrate_servererror;
				break;
			default:
				msgId = R.string.register_activity_registrate_failed;
				break;
			}
			resultMsg = getText(msgId);
		}

		if (ok) {
			Intent resultData = new Intent();
			resultData.putExtra(PARAM_USERNAME, username);
			resultData.putExtra(PARAM_EMAIL, email);
			resultData.putExtra(PARAM_PWD,
					Base64.encodeToString(srpPassword, Base64.DEFAULT));
			setResult(RESULT_OK, resultData);
			finish();
		} else {
			EditText passwordEdit = (EditText) findViewById(R.id.password_edit);
			passwordEdit.setError(pwdErrorText);

			textView.setVisibility(resultMsg != null ? View.VISIBLE : View.GONE);
			textView.setText(resultMsg);

			EditText emailEdit = (EditText) findViewById(R.id.email_edit);
			emailEdit.setError(emailErrorText);

			if (textView.getVisibility() == View.VISIBLE) {
				textView.setError(getText(R.string.register_activity_retry_message));
			} else {
				textView.setError(null);
			}
		}
	}
}
