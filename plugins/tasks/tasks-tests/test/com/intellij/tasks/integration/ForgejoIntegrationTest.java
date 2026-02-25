// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.integration;

import com.google.gson.Gson;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.core.forgejo.ForgejoRepository;
import com.intellij.tasks.core.forgejo.ForgejoRepositoryType;
import com.intellij.tasks.core.forgejo.ForgejoTask;
import com.intellij.tasks.core.forgejo.model.ForgejoIssue;
import com.intellij.tasks.core.forgejo.model.ForgejoProject;
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

@RunWith(JUnit38AssumeSupportRunner.class)
public class ForgejoIntegrationTest extends TaskManagerTestCase {
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private static final String SERVER_URL = System.getProperty("tasks.tests.forgejo.server");
  private static final String TOKEN = System.getProperty("tasks.tests.forgejo.token");
  private ForgejoRepository myRepository;

  // region Server-dependent tests

  public void testIssueFilteringByState() throws Exception {
    final ForgejoProject project = ContainerUtil.find(myRepository.fetchRepos(), p -> "Issue-Filtering-Tests".equals(p.getName()));
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

  public void testFetchIssue() throws Exception {
    myRepository.fetchRepos();
    final ForgejoIssue issue = myRepository.fetchIssue("yopox/test-repo", 1);
    assertNotNull(issue);
    assertEquals(1, issue.getNumber());
    assertNotNull(issue.getTitle());
    assertNotNull(issue.getState());
  }

  public void testUpdatingTimeSpent() throws Exception {
    final ForgejoProject project = ContainerUtil.find(myRepository.fetchRepos(),
                                                      p -> "Time-Tracking-Tests".equals(p.getName()));
    assertNotNull(project);
    myRepository.setCurrentProject(project);

    final Task[] issues = myRepository.getIssues("", 0, 20, false);
    assertTrue(issues.length >= 10);

    int issueNb = (int) (Math.random() * 10);
    final ForgejoTask task = (ForgejoTask)issues[issueNb];
    // Should not throw
    myRepository.updateTimeSpent(new LocalTaskImpl(task), "0h 1m", "");
  }

  // endregion

  // region Issue JSON parsing

  public void testIssueJsonAllFields() {
    String issueJson = """
      {
          "id": 100,
          "number": 7,
          "title": "Add dark mode",
          "body": "Please add dark mode support",
          "state": "closed",
          "html_url": "https://forgejo.tasks-tests.example.com/org/repo/issues/7",
          "updated_at": "2024-01-20T15:45:30Z",
          "created_at": "2024-01-10T09:00:00Z",
          "repository": {
              "id": 42,
              "name": "repo",
              "full_name": "org/repo",
              "html_url": "https://forgejo.tasks-tests.example.com/org/repo"
          }
      }""";

    ForgejoIssue issue = GSON.fromJson(issueJson, ForgejoIssue.class);
    assertEquals(100, issue.getId());
    assertEquals(7, issue.getNumber());
    assertEquals("Add dark mode", issue.getTitle());
    assertEquals("Please add dark mode support", issue.getBody());
    assertEquals("closed", issue.getState());
    assertEquals("https://forgejo.tasks-tests.example.com/org/repo/issues/7", issue.getHtmlUrl());
    assertNotNull(issue.getCreatedAt());
    assertNotNull(issue.getUpdatedAt());

    ForgejoProject repo = issue.getRepository();
    assertNotNull(repo);
    assertEquals(42, repo.getId());
    assertEquals("repo", repo.getName());
    assertEquals("org/repo", repo.getFullName());
    assertEquals("https://forgejo.tasks-tests.example.com/org/repo", repo.getHtmlUrl());
  }

  public void testIssueJsonNullOptionalFields() {
    String issueJson = """
      {
          "id": 200,
          "number": 1,
          "title": "Minimal issue",
          "body": null,
          "state": "open",
          "html_url": null,
          "updated_at": "2024-06-01T00:00:00Z",
          "created_at": "2024-06-01T00:00:00Z",
          "repository": null
      }""";

    ForgejoIssue issue = GSON.fromJson(issueJson, ForgejoIssue.class);
    assertEquals(200, issue.getId());
    assertEquals(1, issue.getNumber());
    assertEquals("Minimal issue", issue.getTitle());
    assertNull(issue.getBody());
    assertEquals("open", issue.getState());
    assertNull(issue.getHtmlUrl());
    assertNull(issue.getRepository());
  }

  public void testIssueJsonOpenState() {
    String issueJson = """
      {
          "id": 1,
          "number": 1,
          "title": "Open",
          "state": "open",
          "updated_at": "2024-01-01T00:00:00Z",
          "created_at": "2024-01-01T00:00:00Z"
      }""";

    ForgejoIssue issue = GSON.fromJson(issueJson, ForgejoIssue.class);
    assertEquals("open", issue.getState());
  }

  public void testIssueJsonClosedState() {
    String issueJson = """
      {
          "id": 2,
          "number": 2,
          "title": "Closed",
          "state": "closed",
          "updated_at": "2024-01-01T00:00:00Z",
          "created_at": "2024-01-01T00:00:00Z"
      }""";

    ForgejoIssue issue = GSON.fromJson(issueJson, ForgejoIssue.class);
    assertEquals("closed", issue.getState());
  }

  public void testMultipleIssuesParsing() {
    String issuesJson = """
      [
        {
          "id": 1, "number": 1, "title": "First", "body": "body1", "state": "open",
          "html_url": "https://forgejo.tasks-tests.example.com/user/repo/issues/1",
          "updated_at": "2024-01-02T00:00:00Z", "created_at": "2024-01-01T00:00:00Z"
        },
        {
          "id": 2, "number": 2, "title": "Second", "body": "body2", "state": "closed",
          "html_url": "https://forgejo.tasks-tests.example.com/user/repo/issues/2",
          "updated_at": "2024-01-03T00:00:00Z", "created_at": "2024-01-01T00:00:00Z"
        }
      ]""";

    ForgejoIssue[] issues = GSON.fromJson(issuesJson, ForgejoIssue[].class);
    assertEquals(2, issues.length);
    assertEquals("First", issues[0].getTitle());
    assertFalse(new ForgejoTask(myRepository, issues[0]).isClosed());
    assertEquals("Second", issues[1].getTitle());
    assertTrue(new ForgejoTask(myRepository, issues[1]).isClosed());
  }

  // endregion

  // region Project JSON parsing

  public void testProjectJsonAllFields() {
    String projectJson = """
      {
         "id": 5,
         "name": "my-project",
         "full_name": "myorg/my-project",
         "html_url": "https://forgejo.tasks-tests.example.com/myorg/my-project"
      }""";

    ForgejoProject project = GSON.fromJson(projectJson, ForgejoProject.class);
    assertEquals(5, project.getId());
    assertEquals("my-project", project.getName());
    assertEquals("myorg/my-project", project.getFullName());
    assertEquals("https://forgejo.tasks-tests.example.com/myorg/my-project", project.getHtmlUrl());
  }

  public void testProjectEquality() {
    ForgejoProject p1 = GSON.fromJson("""
      {"id": 10, "name": "a"}""", ForgejoProject.class);
    ForgejoProject p2 = GSON.fromJson("""
      {"id": 10, "name": "b"}""", ForgejoProject.class);
    ForgejoProject p3 = GSON.fromJson("""
      {"id": 20, "name": "a"}""", ForgejoProject.class);

    assertEquals(p1, p2);
    assertNotSame(p1, p3);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  public void testMultipleProjectsParsing() {
    String projectsJson = """
      [
        {"id": 1, "name": "repo1", "full_name": "user/repo1", "html_url": "https://forgejo.tasks-tests.example.com/user/repo1"},
        {"id": 2, "name": "repo2", "full_name": "org/repo2", "html_url": "https://forgejo.tasks-tests.example.com/org/repo2"}
      ]""";

    ForgejoProject[] projects = GSON.fromJson(projectsJson, ForgejoProject[].class);
    assertEquals(2, projects.length);
    assertEquals("user/repo1", projects[0].getFullName());
    assertEquals("org/repo2", projects[1].getFullName());
  }

  // endregion

  // region ForgejoTask adapter

  public void testTaskAdapterOpenIssue() {
    ForgejoIssue issue = parseIssue(50, 3, "Test issue", "Description here", "open",
                                    "https://forgejo.tasks-tests.example.com/user/repo/issues/3");
    ForgejoTask task = new ForgejoTask(myRepository, issue);

    assertEquals("50", task.getId());
    assertEquals("#3", task.getPresentableId());
    assertEquals("3", task.getNumber());
    assertEquals("Test issue", task.getSummary());
    assertEquals("Description here", task.getDescription());
    assertEquals("https://forgejo.tasks-tests.example.com/user/repo/issues/3", task.getIssueUrl());
    assertFalse(task.isClosed());
    assertTrue(task.isIssue());
    assertEquals(TaskType.BUG, task.getType());
    assertNotNull(task.getIcon());
    assertNotNull(task.getUpdated());
    assertNotNull(task.getCreated());
    assertEquals(myRepository, task.getRepository());
  }

  public void testTaskAdapterClosedIssue() {
    ForgejoIssue issue = parseIssue(60, 5, "Closed issue", "", "closed",
                                    "https://forgejo.tasks-tests.example.com/user/repo/issues/5");
    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertTrue(task.isClosed());
  }

  public void testTaskAdapterNullDescription() {
    String issueJson = """
      {
          "id": 70, "number": 8, "title": "No body",
          "body": null, "state": "open",
          "updated_at": "2024-01-01T00:00:00Z", "created_at": "2024-01-01T00:00:00Z"
      }""";
    ForgejoTask task = new ForgejoTask(myRepository, GSON.fromJson(issueJson, ForgejoIssue.class));
    assertNull(task.getDescription());
  }

  public void testTaskAdapterNullIssueUrl() {
    String issueJson = """
      {
          "id": 80, "number": 9, "title": "No URL",
          "state": "open", "html_url": null,
          "updated_at": "2024-01-01T00:00:00Z", "created_at": "2024-01-01T00:00:00Z"
      }""";
    ForgejoTask task = new ForgejoTask(myRepository, GSON.fromJson(issueJson, ForgejoIssue.class));
    assertNull(task.getIssueUrl());
  }

  public void testTaskAdapterEmptyComments() {
    ForgejoIssue issue = parseIssue(1, 1, "t", "", "open", null);
    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertEquals(0, task.getComments().length);
  }

  public void testTaskProjectFromRepository() {
    ForgejoProject project = GSON.fromJson("""
      {"id": 9, "name": "repo", "full_name": "user/repo"}""", ForgejoProject.class);
    myRepository.setProjects(Collections.singletonList(project));
    myRepository.setCurrentProject(project);

    ForgejoIssue issue = parseIssue(1, 1, "t", "", "open", null);
    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertEquals("user/repo", task.getProject());
  }

  public void testTaskProjectFromIssueRepositoryField() {
    String issueJson = """
      {
          "id": 1, "number": 1, "title": "t", "state": "open",
          "updated_at": "2024-01-01T00:00:00Z", "created_at": "2024-01-01T00:00:00Z",
          "repository": {"id": 5, "name": "r", "full_name": "org/r"}
      }""";
    ForgejoTask task = new ForgejoTask(myRepository, GSON.fromJson(issueJson, ForgejoIssue.class));
    assertEquals("org/r", task.getProject());
  }

  public void testTaskProjectNullWhenUnknown() {
    ForgejoIssue issue = parseIssue(1, 1, "t", "", "open", null);
    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertNull(task.getProject());
  }

  // endregion

  // region Commit message format

  public void testCommitMessageFormat() {
    ForgejoIssue issue = parseIssue(1, 42, "Fix login bug", "Users cannot login with SSO", "open",
                                    "https://forgejo.tasks-tests.example.com/myuser/myrepo/issues/42");
    ForgejoProject project = GSON.fromJson("""
      {"id": 10, "name": "myrepo", "full_name": "myuser/myrepo"}""", ForgejoProject.class);

    myRepository.setProjects(Collections.singletonList(project));
    myRepository.setCurrentProject(project);
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{project} {number} {id} {summary}");

    LocalTaskImpl localTask = new LocalTaskImpl(new ForgejoTask(myRepository, issue));
    String changeListComment = TaskUtil.getChangeListComment(localTask);
    assertEquals("myuser/myrepo 42 #42 Fix login bug", changeListComment);
  }

  public void testCommitMessageFormatWithUnknownProject() {
    ForgejoIssue issue = parseIssue(1, 42, "Fix login bug", "", "open", null);

    myRepository.setProjects(Collections.emptyList());
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{project} {number} {id} {summary}");

    LocalTaskImpl localTask = new LocalTaskImpl(new ForgejoTask(myRepository, issue));
    String changeListComment = TaskUtil.getChangeListComment(localTask);
    // Project is unknown, so "" is substituted instead
    assertEquals("42 #42 Fix login bug", changeListComment);
  }

  public void testDefaultCommitMessage() {
    ForgejoIssue issue = parseIssue(4, 1, "First issue", "", "open",
                                    "https://forgejo.tasks-tests.example.com/user/repo/issues/1");
    ForgejoProject project = GSON.fromJson("""
      {"id": 9, "name": "repo", "full_name": "user/repo"}""", ForgejoProject.class);

    myRepository.setProjects(Collections.singletonList(project));
    myRepository.setCurrentProject(project);
    myRepository.setShouldFormatCommitMessage(true);

    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertEquals("#1 First issue", myRepository.getTaskComment(task));
  }

  // endregion

  // region Repository configuration

  public void testRestApiPathPrefix() {
    assertEquals("/api/v1/", myRepository.getRestApiPathPrefix());
  }

  public void testRestApiUrl() {
    myRepository.setUrl("https://forgejo.tasks-tests.example.com");
    assertEquals("https://forgejo.tasks-tests.example.com/api/v1/repos/owner/repo/issues",
                 myRepository.getRestApiUrl("repos", "owner", "repo", "issues"));
  }

  public void testIsConfiguredRequiresPassword() {
    myRepository.setUrl("https://forgejo.tasks-tests.example.com");
    myRepository.setPassword("some-token");
    assertTrue(myRepository.isConfigured());

    myRepository.setPassword("");
    assertFalse(myRepository.isConfigured());

    myRepository.setPassword(null);
    assertFalse(myRepository.isConfigured());
  }

  public void testExtractId() {
    assertEquals("123", myRepository.extractId("123"));
    assertEquals("1", myRepository.extractId("1"));
    assertNull(myRepository.extractId("abc"));
    assertNull(myRepository.extractId("#123"));
    assertNull(myRepository.extractId(""));
  }

  public void testPresentableName() {
    myRepository.setUrl("https://forgejo.tasks-tests.example.com");
    assertEquals("https://forgejo.tasks-tests.example.com", myRepository.getPresentableName());

    ForgejoProject project = GSON.fromJson("""
      {"id": 1, "name": "repo", "full_name": "user/repo"}""", ForgejoProject.class);
    myRepository.setCurrentProject(project);
    assertEquals("https://forgejo.tasks-tests.example.com/user/repo", myRepository.getPresentableName());

    myRepository.setCurrentProject(ForgejoRepository.UNSPECIFIED_PROJECT);
    assertEquals("https://forgejo.tasks-tests.example.com", myRepository.getPresentableName());
  }

  public void testClone() {
    ForgejoProject project = GSON.fromJson("""
      {"id": 1, "name": "repo", "full_name": "user/repo"}""", ForgejoProject.class);
    myRepository.setCurrentProject(project);
    myRepository.setProjects(Collections.singletonList(project));

    ForgejoRepository cloned = myRepository.clone();
    assertEquals(myRepository, cloned);
    assertNotSame(myRepository, cloned);
    assertEquals(project, cloned.getCurrentProject());
    assertEquals(1, cloned.getProjects().size());
  }

  public void testTimeManagementFeatureEnabled() {
    assertTrue(myRepository.isSupported(TaskRepository.TIME_MANAGEMENT));
  }

  // endregion

  // region XML serialization

  public void testUnspecifiedProjectIdSerialized() {
    myRepository.setCurrentProject(ForgejoRepository.UNSPECIFIED_PROJECT);
    final List<Element> options = XmlSerializer.serialize(myRepository).getChildren("option");
    final String serializedId = StreamEx.of(options)
      .findFirst(elem -> "currentProject".equals(elem.getAttributeValue("name")))
      .map(elem -> elem.getChild("ForgejoProject"))
      .map(elem -> elem.getAttributeValue("id"))
      .orElse(null);
    assertEquals("-1", serializedId);
  }

  public void testProjectSerialized() {
    ForgejoProject project = new ForgejoProject();
    project.setId(42);
    project.setFullName("user/repo");
    myRepository.setCurrentProject(project);

    final List<Element> options = XmlSerializer.serialize(myRepository).getChildren("option");
    final Element projectElement = StreamEx.of(options)
      .findFirst(elem -> "currentProject".equals(elem.getAttributeValue("name")))
      .map(elem -> elem.getChild("ForgejoProject"))
      .orElse(null);
    assertNotNull(projectElement);
    assertEquals("42", projectElement.getAttributeValue("id"));
    assertNull(projectElement.getAttributeValue("fullName"));
  }

  public void testProjectDeserializedWithFullName() {
    ForgejoProject project = new ForgejoProject();
    project.setId(1);
    project.setFullName("myuser/myrepo");
    myRepository.setCurrentProject(project);

    Element serialized = XmlSerializer.serialize(myRepository);
    ForgejoRepository deserialized = XmlSerializer.deserialize(serialized, ForgejoRepository.class);

    ForgejoProject deserializedProject = deserialized.getCurrentProject();
    assertNotNull(deserializedProject);
    assertEquals(1, deserializedProject.getId());
    assertEquals("myuser/myrepo", deserializedProject.getFullName());
  }

  // endregion

  // region Full API response parsing (with extra fields ignored by GSON)

  public void testFullApiIssueResponse() {
    String issueJson = """
      {
          "id": 500,
          "url": "https://forgejo.tasks-tests.example.com/api/v1/repos/org/project/issues/10",
          "html_url": "https://forgejo.tasks-tests.example.com/org/project/issues/10",
          "number": 10,
          "user": {"id": 1, "login": "admin", "full_name": "Admin User"},
          "original_author": "",
          "original_author_id": 0,
          "title": "Full response test",
          "body": "This tests that extra API fields are gracefully ignored by GSON",
          "ref": "",
          "assets": [],
          "labels": [{"id": 1, "name": "bug", "color": "#ee0701"}],
          "milestone": null,
          "assignee": null,
          "assignees": null,
          "state": "open",
          "is_locked": false,
          "comments": 3,
          "created_at": "2024-05-01T10:00:00Z",
          "updated_at": "2024-05-15T14:30:00Z",
          "closed_at": null,
          "due_date": null,
          "pull_request": null,
          "repository": {
              "id": 99,
              "name": "project",
              "owner": "org",
              "full_name": "org/project"
          },
          "pin_order": 0
      }""";

    ForgejoIssue issue = GSON.fromJson(issueJson, ForgejoIssue.class);
    assertEquals(500, issue.getId());
    assertEquals(10, issue.getNumber());
    assertEquals("Full response test", issue.getTitle());
    assertEquals("This tests that extra API fields are gracefully ignored by GSON", issue.getBody());
    assertEquals("open", issue.getState());
    assertEquals("https://forgejo.tasks-tests.example.com/org/project/issues/10", issue.getHtmlUrl());

    ForgejoTask task = new ForgejoTask(myRepository, issue);
    assertEquals("500", task.getId());
    assertEquals("#10", task.getPresentableId());
    assertEquals("10", task.getNumber());
    assertEquals("Full response test", task.getSummary());
    assertFalse(task.isClosed());
  }

  public void testFullApiProjectResponse() {
    String projectJson = """
      {
          "id": 211684,
          "name": "forgejo",
          "full_name": "forgejo/forgejo",
          "description": "A self-hosted forge",
          "empty": false,
          "private": false,
          "fork": false,
          "template": false,
          "mirror": false,
          "archived": false,
          "html_url": "https://forgejo.tasks-tests.example.com/forgejo/forgejo",
          "url": "https://forgejo.tasks-tests.example.com/api/v1/repos/forgejo/forgejo",
          "ssh_url": "git@forgejo.tasks-tests.example.com:forgejo/forgejo.git",
          "clone_url": "https://forgejo.tasks-tests.example.com/forgejo/forgejo.git",
          "stars_count": 100,
          "forks_count": 50,
          "watchers_count": 200,
          "open_issues_count": 42,
          "size": 123456,
          "created_at": "2022-01-01T00:00:00Z",
          "updated_at": "2024-06-01T00:00:00Z",
          "owner": {"id": 1, "login": "forgejo"}
      }""";

    ForgejoProject project = GSON.fromJson(projectJson, ForgejoProject.class);
    assertEquals(211684, project.getId());
    assertEquals("forgejo", project.getName());
    assertEquals("forgejo/forgejo", project.getFullName());
    assertEquals("https://forgejo.tasks-tests.example.com/forgejo/forgejo", project.getHtmlUrl());
    assertEquals("forgejo/forgejo", project.toString());
  }

  // endregion

  // region Helpers

  private static ForgejoIssue parseIssue(int id, int number, String title, String body, String state, String htmlUrl) {
    return GSON.fromJson(String.format("""
      {
          "id": %d,
          "number": %d,
          "title": %s,
          "body": %s,
          "state": "%s",
          "html_url": %s,
          "updated_at": "2024-03-01T12:00:00Z",
          "created_at": "2024-02-28T10:00:00Z"
      }""", id, number, GSON.toJson(title), GSON.toJson(body), state,
                                       htmlUrl != null ? GSON.toJson(htmlUrl) : "null"), ForgejoIssue.class);
  }

  // endregion

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = (ForgejoRepository)new ForgejoRepositoryType().createRepository();
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
        ExternalResourcesChecker.reportUnavailability("Forgejo test server " + SERVER_URL, e);
      }
      throw e;
    }
  }
}
