/**
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

package com.atlassian.theplugin.jira.api;

import java.util.HashMap;
import java.util.Map;

public abstract class JIRAUserBean extends AbstractJIRAConstantBean {
	protected String value = "";

	public JIRAUserBean() {
		super();
	}

	public JIRAUserBean(long id, String name, String value) {
		super(id, name, null);
		this.value = value;
	}

	public JIRAUserBean(Map<String, String> map) {
		super(map);
		value = map.get("value");
    }

	public HashMap<String, String> getMap() {
		HashMap<String, String> map = super.getMap();
		map.put("value", getValue());
		return map;
	}

	public String getValue() {
		return value;
	}

	public abstract String getQueryStringFragment();
}
