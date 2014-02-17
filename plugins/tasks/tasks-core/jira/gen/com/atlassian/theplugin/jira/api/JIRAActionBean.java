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

public class JIRAActionBean extends AbstractJIRAConstantBean implements JIRAAction {
    public JIRAActionBean(long id, String name) {
		super(id, name, null);
    }

	public JIRAActionBean(JIRAActionBean other) {
		this(other.id, other.name);
	}

	public String getQueryStringFragment() {
        return "action=" + id;
    }

	public JIRAActionBean getClone() {
		return new JIRAActionBean(this);
	}

	@Override
	public String toString() {
		return name;
	}
}
