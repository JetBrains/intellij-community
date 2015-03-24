package com.intellij.tasks.integration;

import com.google.gson.Gson;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.gitlab.GitlabRepository;
import com.intellij.tasks.gitlab.GitlabTask;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.impl.gson.TaskGsonUtil;

import java.util.Collections;

/**
 * TODO: install Gitlab on server and add more functional tests
 * @author Mikhail Golubev
 */
public class GitlabIntegrationTest extends TaskManagerTestCase {
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private GitlabRepository myRepository;

  public void testCommitMessageFormat() throws Exception {
    String issueJson = "{\n" +
                       "    \"id\": 1,\n" +
                       "    \"iid\": 2,\n" +
                       "    \"project_id\": 3,\n" +
                       "    \"title\": \"Sample title\",\n" +
                       "    \"state\": \"opened\",\n" +
                       "    \"updated_at\": \"2013-11-14T12:30:39Z\",\n" +
                       "    \"created_at\": \"2013-11-14T12:30:39Z\"\n" +
                       "}";

    String projectJson = "{\n" +
                         "   \"id\": 3,\n" +
                         "   \"name\": \"project-1\"\n" +
                         "}";

    GitlabIssue issue = GSON.fromJson(issueJson, GitlabIssue.class);
    GitlabProject project = GSON.fromJson(projectJson, GitlabProject.class);

    myRepository.setProjects(Collections.singletonList(project));
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{project} {number} {id} {summary}");

    LocalTaskImpl localTask = new LocalTaskImpl(new GitlabTask(myRepository, issue));
    String changeListComment = TaskUtil.getChangeListComment(localTask);
    assertEquals("project-1 2 1 Sample title", changeListComment);

    myRepository.setProjects(Collections.<GitlabProject>emptyList());
    localTask = new LocalTaskImpl(new GitlabTask(myRepository, issue));
    changeListComment = TaskUtil.getChangeListComment(localTask);
    // Project is unknown, so "" is substituted instead
    assertEquals(" 2 1 Sample title", changeListComment);

  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new GitlabRepository();
  }
}
