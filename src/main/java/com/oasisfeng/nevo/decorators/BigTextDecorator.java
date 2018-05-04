/*
 * Copyright (C) 2015 The Nevolution Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.nevo.decorators;

import android.app.Notification;
import android.os.Bundle;

import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

/**
 * Expand truncated single-line text into expandable multi-line long text.
 *
 * @author Oasis
 */
public class BigTextDecorator extends NevoDecoratorService {

	private static final int MIN_TEXT_LENGTH = 20;

	@Override public void apply(final MutableStatusBarNotification evolved) {
		final Notification n = evolved.getNotification();
		if (n.bigContentView != null) return;
		final Bundle extras = n.extras;
		final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
		if (text == null) return;
		if (text.length() < MIN_TEXT_LENGTH) return;

		extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE));
		extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text);
		extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT);
	}
}
