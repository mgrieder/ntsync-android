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

import com.ntsync.android.sync.R;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;

/**
 * This is a series of unit tests for the {@link AuthenticatorActivity} class.
 */
@SmallTest
public class AuthenticatorActivityTest extends
		ActivityInstrumentationTestCase2<AuthenticatorActivity> {

	private static final int ACTIVITY_WAIT = 10000;

	private Instrumentation mInstrumentation;

	private Context mContext;

	public AuthenticatorActivityTest() {

		super(AuthenticatorActivity.class);
	}

	/**
	 * Common setup code for all tests. Sets up a default launch intent, which
	 * some tests will use (others will override).
	 */
	@Override
	protected void setUp() throws Exception {

		super.setUp();
		mInstrumentation = this.getInstrumentation();
		mContext = mInstrumentation.getTargetContext();
	}

	@Override
	protected void tearDown() throws Exception {

		super.tearDown();
	}

	/**
	 * Confirm that Login is presented.
	 */
	@SmallTest
	public void testLoginOffered() {
		Instrumentation.ActivityMonitor monitor = mInstrumentation.addMonitor(
				AuthenticatorActivity.class.getName(), null, false);
		Intent intent = new Intent(mContext, AuthenticatorActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mInstrumentation.startActivitySync(intent);
		Activity activity = mInstrumentation.waitForMonitorWithTimeout(monitor,
				ACTIVITY_WAIT);
		View loginbutton = activity.findViewById(R.id.ok_button);
		int expected = View.VISIBLE;
		assertEquals(expected, loginbutton.getVisibility());
	}
}
