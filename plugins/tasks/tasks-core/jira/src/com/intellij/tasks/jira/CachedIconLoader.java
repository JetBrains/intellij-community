/*
 * Copyright (C) 2008 Atlassian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.tasks.jira;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CachedIconLoader {
	private static final Map<String, Icon> icons = new ConcurrentHashMap<>();

	private CachedIconLoader() {
	}

	public static Icon getIcon(URL url) {
		if (url != null) {
			String key = url.toString();
			if (!icons.containsKey(key)) {
				Icon i = new ImageIcon(url);
				icons.put(key, i);
			}
			return icons.get(key);
		} else {
			return null;
		}
	}

	public static Icon getIcon(String urlString) {
		if (urlString != null) {
			if (!icons.containsKey(urlString)) {
				try {
					URL url = new URL(urlString);
					Icon i = new ImageIcon(url);
					icons.put(urlString, i);
				} catch (MalformedURLException e1) {
					return null;
				}
			}
			return icons.get(urlString);
		} else {
			return null;
		}
	}

}
