package com.intellij.tasks.jira.rest.api20alpha1;

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.rest.JiraRestApi;
import com.intellij.tasks.jira.rest.JiraRestTask;
import com.intellij.tasks.jira.rest.api20alpha1.model.JiraIssueApi20Alpha1;
import com.intellij.tasks.jira.rest.model.JiraIssue;
import com.intellij.tasks.jira.rest.model.JiraResponseWrapper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This REST API is used in JIRA versions from 4.2 to 4.4.
 *
 * @author Mikhail Golubev
 */
public class JiraRestApi20Alpha1 extends JiraRestApi {
  private static final Logger LOG = Logger.getInstance(JiraRestApi20Alpha1.class);
  private static final Type ISSUES_WRAPPER_TYPE = new TypeToken<JiraResponseWrapper.Issues<JiraIssueApi20Alpha1>>() { /* empty */
  }.getType();

  public JiraRestApi20Alpha1(JiraRepository repository) {
    super(repository);
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getPossibleStates(@NotNull Task task) throws Exception {
    return Collections.emptySet();
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

  @NotNull
  @Override
  protected String getRequestForStateTransition(@NotNull CustomTaskState state) {
    final String stateId = state.getId();
    assert StringUtil.isNotEmpty(stateId);
    return "{\"transition\": \"" + stateId + "\"}";
  }

  @Override
  public void updateTimeSpend(@NotNull LocalTask task, @NotNull String timeSpent, String comment) throws Exception {
    throw new Exception(TaskBundle.message("jira.failure.no.time.spent"));
  }

  @NotNull
  @Override
  public ApiType getType() {
    return ApiType.REST_2_0_ALPHA;
  }
}
