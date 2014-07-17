package com.ntsync.android.sync.shared;

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

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import android.util.Log;

import com.ntsync.android.sync.BuildConfig;

/**
 * Helper-Methods for Android-Logging
 */
public final class LogHelper {

	private LogHelper() {
	}

	/**
	 * Will be logged only when Debug-Level is activated (Possible for
	 * Emulator/ADB-Mode) or BuildConfig is DEBUG.
	 * 
	 * @param tag
	 * @param message
	 * @param cause
	 */
	@SuppressWarnings("unused")
	public static void logD(final String tag, String message, Throwable cause) {
		if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
			Log.d(tag, message, cause);
		}
	}

	/**
	 * Will be logged only when Debug-Level is activated (Possible for
	 * Emulator/ADB-Mode) or BuildConfig is DEBUG.
	 * 
	 * @param tag
	 * @param message
	 *            with a {} placeholder for arg
	 * @param arg
	 */
	@SuppressWarnings("unused")
	public static void logD(final String tag, String message, Object arg) {
		if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
			FormattingTuple msg = MessageFormatter.format(message, arg);
			Log.d(tag, msg.getMessage());
		}
	}

	/**
	 * Will be logged only when Debug-Level is activated (Possible for
	 * Emulator/ADB-Mode) or BuildConfig is DEBUG. Logs only the
	 * Exception-Message, but no stacktrace
	 * 
	 * @param tag
	 * @param message
	 * @param cause
	 */
	@SuppressWarnings("unused")
	public static void logDCause(final String tag, String message,
			Throwable cause) {
		if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
			Log.d(tag,
					message + " Cause:"
							+ (cause != null ? cause.getMessage() : null));
		}
	}

	/**
	 * Will be only Logged in Debug-Build-Configuration and when Verbose-Level
	 * is activated.
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logV(final String tag, String message) {
		if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
			Log.v(tag, message);
		}
	}

	/**
	 * Will be always logged.
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logI(final String tag, String message) {
		Log.i(tag, message);
	}

	/**
	 * Will be always logged, but Stracktrace will be printed only in
	 * Debug-Build
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logI(final String tag, String message, Throwable cause) {
		if (BuildConfig.DEBUG) {
			Log.i(tag, message, cause);
		} else {
			Log.i(tag,
					message + " Cause:"
							+ (cause != null ? cause.getMessage() : null));
		}
	}

	/**
	 * Will be always logged.
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logW(final String tag, String message) {
		Log.w(tag, message);
	}

	/**
	 * Will be always logged, but Stracktrace will be printed only in
	 * Debug-Build
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logWCause(final String tag, String message,
			Throwable cause) {
		if (BuildConfig.DEBUG) {
			Log.w(tag, message, cause);
		} else {
			Log.w(tag,
					message + " Cause:"
							+ (cause != null ? cause.getMessage() : null));
		}
	}

	/**
	 * Will be always logged with Stracktrace
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logW(final String tag, String message, Throwable cause) {
		Log.w(tag, message, cause);
	}

	/**
	 * Will be always logged.
	 * 
	 * @param tag
	 * @param message
	 */
	public static void logE(final String tag, String message, Throwable cause) {
		Log.e(tag, message, cause);
	}
}
