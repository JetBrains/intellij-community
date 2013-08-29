package com.intellij.tasks.jira.model.api2;

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRestApi;
import com.intellij.tasks.jira.JiraUtil;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.tasks.jira.model.JiraResponseWrapper;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This REST API version is used in JIRA 5.1.8 and above (including JIRA 6.x.x).
 *
 * @author Mikhail Golubev
 */
public class JiraRestApi2 extends JiraRestApi {
  private static final Logger LOG = Logger.getInstance(JiraIssueApi2.class);
  private static final Type ISSUES_WRAPPER_TYPE = new TypeToken<JiraResponseWrapper.Issues<JiraIssueApi2>>() { /* empty */
  }.getType();

  public JiraRestApi2(JiraRepository repository) {
    super(repository);
  }

  @NotNull
  @Override
  protected GetMethod getMultipleIssuesSearchMethod(String jql, int max) {
    GetMethod method = super.getMultipleIssuesSearchMethod(jql, max);
    method.setQueryString(method.getQueryString() + "&fields=" + JiraIssueApi2.REQUIRED_RESPONSE_FIELDS);
    return method;
  }

  @NotNull
  @Override
  protected List<JiraIssue> parseIssues(String response) {
    JiraResponseWrapper.Issues<JiraIssueApi2> wrapper = JiraUtil.GSON.fromJson(response, ISSUES_WRAPPER_TYPE);
    return new ArrayList<JiraIssue>(wrapper.getIssues());
  }

  @NotNull
  @Override
  protected GetMethod getSingleIssueSearchMethod(String key) {
    final GetMethod method = super.getSingleIssueSearchMethod(key);
    final String oldParams = method.getQueryString() == null ? "" : method.getQueryString();
    method.setQueryString(oldParams + "&fields=" + JiraIssueApi2.REQUIRED_RESPONSE_FIELDS);
    return method;
  }

  @Nullable
  @Override
  protected JiraIssue parseIssue(String response) {
    return JiraUtil.GSON.fromJson(response, JiraIssueApi2.class);
  }

  @NotNull
  @Override
  public String getVersionName() {
    return "2.0";
  }
}
