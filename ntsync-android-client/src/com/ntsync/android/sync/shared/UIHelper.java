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
 *
 *
 *
 * License for Method computeScrollDeltaToGetChildRectOnScreen:  
 * 
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"):
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;

public final class UIHelper {

	private UIHelper() {
	}

	/**
	 * Ensures a ChildView is visible in a ScrollView.
	 * 
	 * @param scrollView
	 *            null is not allowed
	 * @param child
	 */
	public static void scrollToDeepChild(ScrollView scrollView, View child) {
		Point childOffset = new Point();

		getDeepChildOffset(scrollView, child.getParent(), child, childOffset);

		Rect childRect = new Rect(childOffset.x, childOffset.y, childOffset.x
				+ child.getWidth(), childOffset.y + child.getHeight());

		int deltay = computeScrollDeltaToGetChildRectOnScreen(scrollView,
				childRect);
		scrollView.smoothScrollBy(0, deltay);
	}

	private static void getDeepChildOffset(ScrollView scrollView,
			ViewParent nextParent, View nextChild, Point accumulatedOffset) {
		ViewGroup parent = (ViewGroup) nextParent;
		accumulatedOffset.x += nextChild.getLeft();
		accumulatedOffset.y += nextChild.getTop();
		if (parent == scrollView || parent == null) {
			return;
		}
		getDeepChildOffset(scrollView, parent.getParent(), parent,
				accumulatedOffset);
	}

	/**
	 * Copy of proctected scrollview-method
	 * 
	 * @param scrollview
	 * @param rect
	 * @return
	 */
	protected static int computeScrollDeltaToGetChildRectOnScreen(
			ScrollView scrollview, Rect rect) {
		if (scrollview.getChildCount() == 0) {
			return 0;
		}

		int height = scrollview.getHeight();
		int screenTop = scrollview.getScrollY();
		int screenBottom = screenTop + height;

		int fadingEdge = scrollview.getVerticalFadingEdgeLength();

		// leave room for top fading edge as long as rect isn't at very top
		if (rect.top > 0) {
			screenTop += fadingEdge;
		}

		// leave room for bottom fading edge as long as rect isn't at very
		// bottom
		if (rect.bottom < scrollview.getChildAt(0).getHeight()) {
			screenBottom -= fadingEdge;
		}

		int scrollYDelta = 0;

		if (rect.bottom > screenBottom && rect.top > screenTop) {
			// need to move down to get it in view: move down just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if (rect.height() > height) {
				// just enough to get screen size chunk on
				scrollYDelta += (rect.top - screenTop);
			} else {
				// get entire rect at bottom of screen
				scrollYDelta += (rect.bottom - screenBottom);
			}

			// make sure we aren't scrolling beyond the end of our content
			int bottom = scrollview.getChildAt(0).getBottom();
			int distanceToBottom = bottom - screenBottom;
			scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

		} else if (rect.top < screenTop && rect.bottom < screenBottom) {
			// need to move up to get it in view: move up just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if (rect.height() > height) {
				// screen size chunk
				scrollYDelta -= (screenBottom - rect.bottom);
			} else {
				// entire rect at top
				scrollYDelta -= (screenTop - rect.top);
			}

			// make sure we aren't scrolling any further than the top our
			// content
			scrollYDelta = Math.max(scrollYDelta, -scrollview.getScrollY());
		}
		return scrollYDelta;
	}
}
