package com.ntsync.android.sync.client;

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

/**
 * Used when the server returned an abnormal response (like Internal Server
 * Error) -> netzwerkverbindung meistens ok, aber ein Problem auf dem Server. 
 */
public class ServerException extends Exception {

	private static final long serialVersionUID = 1L;

	public ServerException(String s) {
		super(s);
	}

	public ServerException(String s, Exception ex) {
		super(s, ex);
	}
}
