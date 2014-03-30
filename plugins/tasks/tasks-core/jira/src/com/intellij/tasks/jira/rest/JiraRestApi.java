package com.intellij.tasks.jira.rest;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.JiraRemoteApi;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraVersion;
import com.intellij.tasks.jira.rest.api2.JiraRestApi2;
import com.intellij.tasks.jira.rest.api20alpha1.JiraRestApi20Alpha1;
import com.intellij.tasks.jira.rest.model.JiraIssue;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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
public abstract class JiraRestApi extends JiraRemoteApi {
  private static final Logger LOG = Logger.getInstance(JiraRestApi.class);

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

  protected JiraRestApi(@NotNull JiraRepository repository) {
    super(repository);
  }

  @Override
  @NotNull
  public final List<Task> findTasks(String jql, int max) throws Exception {
    GetMethod method = getMultipleIssuesSearchMethod(jql, max);
    String response = myRepository.executeMethod(method);
    List<JiraIssue> issues = parseIssues(response);
    LOG.debug("Total " + issues.size() + " downloaded");
    return ContainerUtil.map(issues, new Function<JiraIssue, Task>() {
      @Override
      public JiraRestTask fun(JiraIssue issue) {
        return new JiraRestTask(issue, myRepository);
      }
    });
  }

  @Override
  @Nullable
  public final JiraRestTask findTask(String key) throws Exception {
    GetMethod method = getSingleIssueSearchMethod(key);
    return new JiraRestTask(parseIssue(myRepository.executeMethod(method)), myRepository);
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

  protected static RequestEntity createJsonEntity(String requestBody) {
    try {
      return new StringRequestEntity(requestBody, "application/json", CharsetToolkit.UTF8);
    }
    catch (UnsupportedEncodingException e) {
      throw new AssertionError("UTF-8 encoding is not supported");
    }
  }
}
