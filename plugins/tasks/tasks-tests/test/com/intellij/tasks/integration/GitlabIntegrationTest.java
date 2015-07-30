package com.intellij.tasks.integration;

import com.google.gson.Gson;
import com.intellij.openapi.util.Condition;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.gitlab.GitlabRepository;
import com.intellij.tasks.gitlab.GitlabTask;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.impl.gson.GsonUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;

/**
 * TODO: install Gitlab on server and add more functional tests
 * @author Mikhail Golubev
 */
public class GitlabIntegrationTest extends TaskManagerTestCase {
  private static final Gson GSON = GsonUtil.createDefaultBuilder().create();
  private static final String SERVER_URL = "http://trackers-tests.labs.intellij.net:8045";
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

  public void testIssueFilteringByState() throws Exception {
    final GitlabProject project = ContainerUtil.find(myRepository.getProjects(), new Condition<GitlabProject>() {
      @Override
      public boolean value(GitlabProject p) {
        return p.getName().equals("Issue Filtering Tests");
      }
    });
    assertNotNull(project);
    myRepository.setCurrentProject(project);

    final Task[] allIssues = myRepository.getIssues("", 0, 20, true);
    assertSize(2, allIssues);
    assertNotNull(ContainerUtil.find(allIssues, new Condition<Task>() {
      @Override
      public boolean value(Task task) {
        return task.isClosed() && task.getSummary().equals("Closed issue #1");
      }
    }));
    assertNotNull(ContainerUtil.find(allIssues, new Condition<Task>() {
      @Override
      public boolean value(Task task) {
        return !task.isClosed() && task.getSummary().equals("Opened issue #1");
      }
    }));

    final Task[] openedIssues = myRepository.getIssues("", 0, 20, false);
    assertSize(1, openedIssues);
    assertFalse(openedIssues[0].isClosed());
    assertEquals("Opened issue #1", openedIssues[0].getSummary());
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new GitlabRepository();
    myRepository.setUrl(SERVER_URL);
    myRepository.setPassword("PqbBxWaqFxZijQXKPLLo"); // buildtest
  }
}
