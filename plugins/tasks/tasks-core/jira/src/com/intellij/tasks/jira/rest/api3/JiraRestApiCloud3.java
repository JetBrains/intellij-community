// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.api3;

import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.rest.api2.JiraRestApi2;
import com.intellij.tasks.jira.rest.api2.model.JiraIssueApi2;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;

/**
 * For now (25.09.2025) released on Cloud only;
 * for on premises see <a href="https://jira.atlassian.com/browse/JRASERVER-70688">the issue</a>.
 * <p>
 * Later this class is expected to end up in two:
 * separate JiraRestApi3 and probably JiraRestApiCloud with experiments
 */
public class JiraRestApiCloud3 extends JiraRestApi2 {
  public JiraRestApiCloud3(JiraRepository repository) {
    super(repository);
  }

  @Override
  public @NotNull ApiType getType() {
    return ApiType.REST_3_CLOUD;
  }

  @Override
  protected @NotNull GetMethod getMultipleIssuesSearchMethod(String jql, int max) {
    GetMethod method = new GetMethod(myRepository.getRestUrl("search/jql"));
    method.setQueryString(new NameValuePair[]{
      new NameValuePair("jql", jql),
      new NameValuePair("maxResults", String.valueOf(max)),
      new NameValuePair("fields", JiraIssueApi2.REQUIRED_RESPONSE_FIELDS)
    });
    return method;
  }
}
