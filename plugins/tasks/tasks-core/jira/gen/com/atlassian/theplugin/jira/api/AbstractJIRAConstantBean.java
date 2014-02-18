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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractJIRAConstantBean implements JIRAConstant {
    protected String name;
    protected long id;
    protected URL iconUrl = null;

	public AbstractJIRAConstantBean() {		
	}

	protected AbstractJIRAConstantBean(long id, String name, URL iconUrl) {
		this.id = id;
		this.name = name;
		this.iconUrl = iconUrl;
	}

	public AbstractJIRAConstantBean(Map<String, String> map) {
        name = map.get("name");
        id = Long.valueOf(map.get("id"));

        if (map.containsKey("icon")) {
            try {
                iconUrl = new URL(map.get("icon"));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

	public HashMap<String, String> getMap() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("name", getName());
		map.put("id", Long.toString(id));
		if (iconUrl != null) {
			map.put("icon", iconUrl.toString());
		}
		map.put("filterTypeClass", this.getClass().getName());
		return map;
	}

	public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public URL getIconUrl() {
        return iconUrl;
    }
}
