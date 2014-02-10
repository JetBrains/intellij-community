package com.intellij.tasks.jira;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.tasks.jira.model.api2.JiraRestApi2;
import com.intellij.tasks.jira.model.api20alpha1.JiraRestApi20Alpha1;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class JiraRestApi {
  private static final Logger LOG = Logger.getInstance(JiraRestApi.class);
  protected final JiraRepository myRepository;

  public static JiraRestApi fromJiraVersion(@NotNull JiraVersion jiraVersion, @NotNull JiraRepository repository) {
    LOG.debug("JIRA version is " + jiraVersion);
    if (jiraVersion.getMajorNumber() == 4 && jiraVersion.getMinorNumber() >= 2) {
      return new JiraRestApi20Alpha1(repository);
    }
    else if (jiraVersion.getMajorNumber() >= 5) {
      return new JiraRestApi2(repository);
    }
    else {
      LOG.warn("JIRA below 4.2.0 doesn't support REST API (" + jiraVersion + " used)");
      return null;
    }
  }

  public static JiraRestApi fromJiraVersion(@NotNull String version, @NotNull JiraRepository repository) {
    return fromJiraVersion(new JiraVersion(version), repository);
  }

  protected JiraRestApi(JiraRepository repository) {
    myRepository = repository;
  }

  @NotNull
  public final List<JiraIssue> findIssues(String jql, int max) throws Exception {
    GetMethod method = getMultipleIssuesSearchMethod(jql, max);
    String response = myRepository.executeMethod(method);
    List<JiraIssue> issues = parseIssues(response);
    LOG.debug("Total " + issues.size() + " downloaded");
    return issues;
  }

  @Nullable
  public final JiraIssue findIssue(String key) throws Exception {
    GetMethod method = getSingleIssueSearchMethod(key);
    return parseIssue(myRepository.executeMethod(method));
  }

  @NotNull
  protected GetMethod getSingleIssueSearchMethod(String key) {
    return new GetMethod(myRepository.getRestUrl("issue", key));
  }

  @NotNull
  protected GetMethod getMultipleIssuesSearchMethod(String jql, int max) {
    GetMethod method = new GetMethod(myRepository.getRestUrl("search"));
    method.setQueryString(new NameValuePair[]{
      new NameValuePair("jql", jql),
      new NameValuePair("maxResults", String.valueOf(max))
    });
    return method;
  }

  @NotNull
  protected abstract List<JiraIssue> parseIssues(String response);

  @Nullable
  protected abstract JiraIssue parseIssue(String response);

  @Override
  public String toString() {
    return String.format("JiraRestAPI(%s)", getVersionName());
  }

  @NotNull
  public abstract String getVersionName();

  public void setTaskState(Task task, TaskState state) throws Exception {
    String requestBody = getRequestForStateTransition(state);
    LOG.debug(String.format("Transition: %s -> %s, request: %s", task.getState(), state, requestBody));
    if (requestBody == null) {
      return;
    }
    PostMethod method = new PostMethod(myRepository.getRestUrl("issue", task.getId(), "transitions"));
    method.setRequestEntity(createJsonEntity(requestBody));
    myRepository.executeMethod(method);
  }

  @Nullable
  protected abstract String getRequestForStateTransition(@NotNull TaskState state);

  public abstract void updateTimeSpend(LocalTask task, String timeSpent, String comment) throws Exception;

  protected static RequestEntity createJsonEntity(String requestBody) {
    try {
      return new StringRequestEntity(requestBody, "application/json", CharsetToolkit.UTF8);
    }
    catch (UnsupportedEncodingException e) {
      throw new AssertionError("UTF-8 encoding is not supported");
    }
  }
}
