package com.intellij.tasks.integration;

import com.google.gson.Gson;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.gitlab.GitlabRepository;
import com.intellij.tasks.gitlab.GitlabRepositoryType;
import com.intellij.tasks.gitlab.GitlabTask;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
@RunWith(JUnit38AssumeSupportRunner.class)
public class GitlabIntegrationTest extends TaskManagerTestCase {
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private static final String SERVER_URL = System.getProperty("tasks.tests.gitlab.server");
  private static final String TOKEN = System.getProperty("tasks.tests.gitlab.password");
  private GitlabRepository myRepository;

  public void testCommitMessageFormat() {
    String issueJson = """
      {
          "id": 1,
          "iid": 2,
          "project_id": 3,
          "title": "Sample title",
          "state": "opened",
          "updated_at": "2013-11-14T12:30:39Z",
          "created_at": "2013-11-14T12:30:39Z"
      }""";

    String projectJson = """
      {
         "id": 3,
         "name": "project-1"
      }""";

    GitlabIssue issue = GSON.fromJson(issueJson, GitlabIssue.class);
    GitlabProject project = GSON.fromJson(projectJson, GitlabProject.class);

    myRepository.setProjects(Collections.singletonList(project));
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{project} {number} {id} {summary}");

    LocalTaskImpl localTask = new LocalTaskImpl(new GitlabTask(myRepository, issue));
    String changeListComment = TaskUtil.getChangeListComment(localTask);
    assertEquals("project-1 2 #2 Sample title", changeListComment);

    myRepository.setProjects(Collections.emptyList());
    localTask = new LocalTaskImpl(new GitlabTask(myRepository, issue));
    changeListComment = TaskUtil.getChangeListComment(localTask);
    // Project is unknown, so "" is substituted instead
    assertEquals("2 #2 Sample title", changeListComment);
  }

  public void testIssueFilteringByState() throws Exception {
    final GitlabProject project = ContainerUtil.find(myRepository.fetchProjects(), p -> p.getName().equals("Issue Filtering Tests"));
    assertNotNull(project);
    myRepository.setCurrentProject(project);

    final Task[] allIssues = myRepository.getIssues("", 0, 20, true);
    assertSize(2, allIssues);
    assertNotNull(ContainerUtil.find(allIssues, task -> task.isClosed() && task.getSummary().equals("Closed Issue #1")));
    assertNotNull(ContainerUtil.find(allIssues, task -> !task.isClosed() && task.getSummary().equals("Opened Issue #1")));

    final Task[] openedIssues = myRepository.getIssues("", 0, 20, false);
    assertSize(1, openedIssues);
    assertFalse(openedIssues[0].isClosed());
    assertEquals("Opened Issue #1", openedIssues[0].getSummary());
  }

  // IDEA-136499
  public void testPresentableId() throws Exception {
    final GitlabIssue issue = myRepository.fetchIssue(9 /* ID Formatting Tests */, 1);
    assertNotNull(issue);
    assertEquals(4, issue.getId());
    assertEquals(1, issue.getLocalId());
    assertEquals(9, issue.getProjectId());

    final GitlabTask task = new GitlabTask(myRepository, issue);
    assertEquals("#1", task.getPresentableId());
    assertEquals("1", task.getNumber());
    assertEquals("ID Formatting Tests", task.getProject());
    assertEquals("4", task.getId());
    assertEquals("#1: First issue", task.toString());
    myRepository.setShouldFormatCommitMessage(true);
    assertEquals("#1 First issue", myRepository.getTaskComment(task));
  }

  public void testUpdatingTimeSpent() throws Exception {
    final GitlabIssue issue = myRepository.fetchIssue(8 /* Time Tracking Tests */, 2);
    final int secondsBefore = issue.getTimeSpent();
    assertNotNull(issue);

    final GitlabTask task = new GitlabTask(myRepository, issue);
    myRepository.updateTimeSpent(new LocalTaskImpl(task), "1s", "");

    final GitlabIssue issue_updated = myRepository.fetchIssue(8 /* Time Tracking Tests */, 2);
    assertNotNull(issue_updated);
    assertTrue(issue_updated.getTimeSpent() > secondsBefore);
  }

  // IDEA-198199
  public void testUnspecifiedProjectIdSerialized() {
    myRepository.setCurrentProject(GitlabRepository.UNSPECIFIED_PROJECT);
    final List<Element> options = XmlSerializer.serialize(myRepository).getChildren("option");
    final String serializedId = StreamEx.of(options)
      .findFirst(elem -> "currentProject".equals(elem.getAttributeValue("name")))
      .map(elem -> elem.getChild("GitlabProject"))
      .map(elem -> elem.getAttributeValue("id"))
      .orElse(null);
    assertEquals("-1", serializedId);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = (GitlabRepository)new GitlabRepositoryType().createRepository();
    myRepository.setUrl(SERVER_URL);
    myRepository.setPassword(TOKEN);
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable);
    }
    catch (Throwable e) {
      if (ExceptionUtil.causedBy(e, IOException.class)) {
        ExternalResourcesChecker.reportUnavailability("GitLab test server " + SERVER_URL, e);
      }
      throw e;
    }
  }
}
