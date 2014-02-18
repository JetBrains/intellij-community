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

import java.util.Map;

public class JIRAComponentBean extends AbstractJIRAConstantBean {
    public JIRAComponentBean(Map<String, String> map) {
        super(map);
    }
	
	public JIRAComponentBean(long id, String name) {
		super(id, name, null);
	}

	public JIRAComponentBean(JIRAComponentBean other) {
		this(other.getMap());
	}

	// returns from this object a fragment of a query string that the IssueNavigator will understand
	public String getQueryStringFragment() {
        return "component=" + getId();
	}

	public JIRAComponentBean getClone() {
		return new JIRAComponentBean(this);
	}
}
