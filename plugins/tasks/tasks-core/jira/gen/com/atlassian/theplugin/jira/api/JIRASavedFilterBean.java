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

public class JIRASavedFilterBean implements JIRASavedFilter {
    private String name;
    private String author;
    private String project;
    private long id;

    public JIRASavedFilterBean(Map projMap) {
        name = (String) projMap.get("name");
        author = (String) projMap.get("author");
        project = (String) projMap.get("project");
        id = Long.valueOf((String) projMap.get("id"));
    }

	public JIRASavedFilterBean(String n, long id) {
		name = n;
		this.id = id;
	}

	public JIRASavedFilterBean(JIRASavedFilterBean other) {
		this(other.getMap());
	}

	public String getName() {
        return name;
    }

	public HashMap<String, String> getMap() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("name", getName());
		map.put("id", Long.toString(id));
		map.put("author", getAuthor());
		map.put("project", getProject());
		map.put("filterTypeClass", this.getClass().getName());
		return map;
	}

	public JIRASavedFilterBean getClone() {
		return new JIRASavedFilterBean(this);
	}

	public long getId() {
        return id;
    }

	public String getAuthor() {
		return author;
	}

	public String getProject() {
		return project;
	}

	public String getQueryStringFragment() {
        return Long.toString(id);
    }
}
