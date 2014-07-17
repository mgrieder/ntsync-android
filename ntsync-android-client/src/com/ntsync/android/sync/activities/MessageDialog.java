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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.ntsync.android.sync.R;
import com.ntsync.android.sync.platform.HoneycombHelper;

/**
 * Provides a general Message Dialog
 */
public class MessageDialog extends DialogFragment {

	private static final String PARAM_TEXTID = "textid";

	private static final String PARAM_TEXT = "text";

	private static final String PARAM_TITLE = "title";

	private static final String PARAM_CLOSE = "close";

	private static final String PARAM_POSITIVEBUTTONTEXT = "posbutton";

	private int textId;

	private CharSequence text;

	private CharSequence title;

	/** If set, a confirmation-dialog will be displayed. */
	private CharSequence positiveBtnText;

	private boolean positivePressed = false;

	private boolean closeActivity;

	/**
	 * Creates a Message Dialog and shows a Text-Message.
	 * 
	 * @param resId
	 * @param activity
	 * 
	 * @return invisible Dialog
	 */
	public static MessageDialog show(int resId, FragmentActivity activity) {
		Bundle args = new Bundle();
		args.putInt(PARAM_TEXTID, resId);
		return showDialog(args, activity, String.valueOf(resId));
	}

	/**
	 * Show Message Dialog from a Text and Activity
	 * 
	 * @param text
	 * @param fragmentActivity
	 * @return
	 */
	public static MessageDialog show(CharSequence text,
			FragmentActivity fragmentActivity) {
		return show(text, fragmentActivity.getSupportFragmentManager());
	}

	/**
	 * Creates a Message Dialog and shows a Text-Message.
	 * 
	 * @param text
	 * @param fragmentManager
	 * 
	 * @return visible Dialog
	 */
	public static MessageDialog show(CharSequence text,
			FragmentManager fragmentManager) {
		Bundle args = new Bundle();
		args.putCharSequence(PARAM_TEXT, text);
		return showDialog(args, fragmentManager,
				String.valueOf(text.hashCode()));
	}

	/**
	 * Creates a Message Dialog and shows a Text-Message.
	 * 
	 * @param resId
	 * @param activity
	 * 
	 * @return visible Dialog
	 */
	public static MessageDialog showConfirmation(CharSequence msgText,
			CharSequence title, CharSequence positiveButtonText,
			FragmentActivity activity) {
		Bundle args = new Bundle();
		args.putCharSequence(PARAM_TEXT, msgText);
		args.putCharSequence(PARAM_TITLE, title);
		args.putCharSequence(PARAM_POSITIVEBUTTONTEXT, positiveButtonText);
		return showDialog(args, activity, String.valueOf(title.hashCode()));
	}

	/**
	 * Shows a Dialog and closes the Activity when the Dialog will be closed.
	 * 
	 * @param resId
	 * @param activity
	 * @return visible Dialog
	 */
	public static MessageDialog showAndClose(int resId,
			FragmentActivity activity) {
		Bundle args = new Bundle();
		args.putInt(PARAM_TEXTID, resId);
		args.putBoolean(PARAM_CLOSE, true);
		return showDialog(args, activity, String.valueOf(resId));
	}

	private static MessageDialog showDialog(Bundle args,
			FragmentActivity activity, String dialogId) {
		return showDialog(args, activity.getSupportFragmentManager(), dialogId);
	}

	private static MessageDialog showDialog(Bundle args,
			FragmentManager fragmentManager, String dialogId) {
		MessageDialog dlg = new MessageDialog();
		dlg.setArguments(args);
		dlg.show(fragmentManager, "MessageDialog" + dialogId);
		return dlg;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = getArguments();
		textId = args.getInt(PARAM_TEXTID);
		text = args.getCharSequence(PARAM_TEXT);
		title = args.getCharSequence(PARAM_TITLE);
		positiveBtnText = args.getCharSequence(PARAM_POSITIVEBUTTONTEXT);

		closeActivity = args.getBoolean(PARAM_CLOSE, false);

	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		if (text != null) {
			builder.setMessage(text);
		} else {
			builder.setMessage(textId);
		}

		if (positiveBtnText != null) {
			builder.setNegativeButton(R.string.cancel_button_label, null);
			builder.setPositiveButton(positiveBtnText, new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					positivePressed = true;
				}
			});
			setupAlertIcon(builder);
		} else {
			builder.setPositiveButton(R.string.ok_button_label, null);
		}
		if (title != null) {
			builder.setTitle(title);
		}

		// Create the AlertDialog object and return it
		return builder.create();
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupAlertIcon(Builder builder) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			HoneycombHelper.setAlertIcon(builder);
		} else {
			builder.setIcon(android.R.drawable.ic_dialog_alert);
		}
	}

	/**
	 * The activity that creates an instance of this dialog fragment can
	 * implement this interface in order to receive event callbacks.
	 */
	public interface DialogClosedListener {

		/**
		 * Called when the Dialog will be closed. If "closeActivity" is true
		 * Activity will be closed after this call.
		 */
		void messageDialogClosed(int dialogId);
	}

	/**
	 * The activity that creates an instance of this dialog fragment can
	 * implement this interface in order to receive event callbacks.
	 */
	public interface ConfirmationDialogClosedListener {

		/**
		 * Called when the Dialog will be closed. If "closeActivity" is true
		 * Activity will be closed after this call.
		 */
		void messageDialogClosed(CharSequence dlgTitle,
				boolean positiveBtnPressed);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if (resultListener != null && positiveBtnText == null) {
			resultListener.messageDialogClosed(textId);
		}
		if (resultListener2 != null && positiveBtnText != null) {
			resultListener2.messageDialogClosed(title, positivePressed);
		}
		if (closeActivity) {
			Activity activity = getActivity();
			if (activity != null) {
				activity.finish();
			}
		}
	}

	private DialogClosedListener resultListener;
	private ConfirmationDialogClosedListener resultListener2;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (activity instanceof ConfirmationDialogClosedListener) {
			resultListener2 = (ConfirmationDialogClosedListener) activity;
		} else {
			resultListener2 = null;
		}
		if (activity instanceof DialogClosedListener) {
			resultListener = (DialogClosedListener) activity;
		} else {
			resultListener = null;
		}
	}
}
