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

import java.util.List;

public interface JIRAIssue {
    String getServerUrl();

    String getProjectUrl();

    String getIssueUrl();

    Long getId();

    String getKey();

    String getProjectKey();

	String getStatus();

	String getStatusTypeUrl();

	String getSummary();

    String getType();

    String getTypeIconUrl();

	String getPriority();

	String getPriorityIconUrl();

	String getDescription();

    JIRAConstant getTypeConstant();

	JIRAConstant getStatusConstant();

	JIRAConstant getPriorityConstant();	

	String getAssignee();

    String getAssigneeId();

    String getReporter();

    String getReporterId();

    String getResolution();

	String getCreated();

	String getUpdated();

	boolean isSubTask();

	String getParentIssueKey();

	List<String> getSubTaskKeys();

	long getPriorityId();

	long getStatusId();

	long getTypeId();

	void setAssignee(String assignee);

	List<JIRAConstant> getAffectsVersions();

	List<JIRAConstant> getFixVersions();
	
	List<JIRAConstant> getComponents();

	void setAffectsVersions(List<JIRAConstant> versions);

	void setFixVersions(List<JIRAConstant> versions);

	void setComponents(List<JIRAConstant> components);

	String getOriginalEstimate();

	void setOriginalEstimate(String t);

	String getRemainingEstimate();

	void setRemainingEstimate(String t);

	String getTimeSpent();

	void setTimeSpent(String t);

	List<JIRAComment> getComments();
}
