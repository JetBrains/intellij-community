package com.intellij.tasks.jira.model.api20alpha1;

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRestApi;
import com.intellij.tasks.jira.JiraUtil;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.tasks.jira.model.JiraResponseWrapper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This REST API is used in JIRA 4.3.4 and 4.4.1.
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
    return JiraUtil.GSON.fromJson(response, JiraIssueApi20Alpha1.class);
  }

  @NotNull
  @Override
  protected List<JiraIssue> parseIssues(String response) {
    JiraResponseWrapper.Issues<JiraIssueApi20Alpha1> wrapper = JiraUtil.GSON.fromJson(response, ISSUES_WRAPPER_TYPE);
    List<JiraIssueApi20Alpha1> incompleteIssues = wrapper.getIssues();
    List<JiraIssue> updatedIssues = new ArrayList<JiraIssue>();
    for (JiraIssueApi20Alpha1 issue : incompleteIssues) {
      try {
        updatedIssues.add(findIssue(issue.getKey()));
      }
      catch (Exception e) {
        LOG.warn("Can't fetch detailed info about issue: " + issue.getKey());
      }
    }
    return updatedIssues;
  }

  @NotNull
  @Override
  public String getVersionName() {
    return "2.0.alpha1";
  }
}
