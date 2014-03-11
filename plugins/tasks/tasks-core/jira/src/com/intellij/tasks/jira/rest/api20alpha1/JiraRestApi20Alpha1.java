package com.intellij.tasks.jira.rest.api20alpha1;

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.rest.JiraRestApi;
import com.intellij.tasks.jira.rest.JiraRestTask;
import com.intellij.tasks.jira.rest.api20alpha1.model.JiraIssueApi20Alpha1;
import com.intellij.tasks.jira.rest.model.JiraIssue;
import com.intellij.tasks.jira.rest.model.JiraResponseWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This REST API is used in JIRA versions from 4.2 to 4.4.
 * @author Mikhail Golubev
 */
public class JiraRestApi20Alpha1 extends JiraRestApi {
  private static final Logger LOG = Logger.getInstance(JiraRestApi20Alpha1.class);
  private static final Type ISSUES_WRAPPER_TYPE = new TypeToken<JiraResponseWrapper.Issues<JiraIssueApi20Alpha1>>() { /* empty */ }.getType();

  public JiraRestApi20Alpha1(JiraRepository repository) {
    super(repository);
  }

  @Override
  protected JiraIssue parseIssue(String response) {
    return JiraRepository.GSON.fromJson(response, JiraIssueApi20Alpha1.class);
  }

  @NotNull
  @Override
  protected List<JiraIssue> parseIssues(String response) {
    JiraResponseWrapper.Issues<JiraIssueApi20Alpha1> wrapper = JiraRepository.GSON.fromJson(response, ISSUES_WRAPPER_TYPE);
    List<JiraIssueApi20Alpha1> incompleteIssues = wrapper.getIssues();
    List<JiraIssue> updatedIssues = new ArrayList<JiraIssue>();
    for (JiraIssueApi20Alpha1 issue : incompleteIssues) {
      try {
        JiraRestTask task = findTask(issue.getKey());
        if (task != null) {
          updatedIssues.add(task.getJiraIssue());
        }
      }
      catch (Exception e) {
        LOG.warn("Can't fetch detailed info about issue: " + issue.getKey());
      }
    }
    return updatedIssues;
  }

  @Nullable
  @Override
  protected String getRequestForStateTransition(@NotNull TaskState state) {
    switch (state) {
      case IN_PROGRESS:
        return  "{\"transition\": \"4\"}";
      case RESOLVED:
        // 5 for "Resolved", 2 for "Closed"
        return  "{\"transition\": \"5\", \"resolution\": \"Fixed\"}";
      case REOPENED:
        return  "{\"transition\": \"3\"}";
      default:
        return null;
    }
  }

  @Override
  public void updateTimeSpend(LocalTask task, String timeSpent, String comment) throws Exception {
    throw new Exception(TaskBundle.message("jira.failure.no.state.update"));
  }

  @NotNull
  @Override
  public ApiType getType() {
    return ApiType.REST_2_0_ALPHA;
  }
}
