package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.redmine.RedmineRepository;
import com.intellij.tasks.redmine.RedmineRepositoryType;
import com.intellij.tasks.redmine.model.RedmineProject;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class RedmineIntegrationTest extends TaskManagerTestCase {
  private static final String REDMINE_2_0_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8072";
  private static final String API_ACCESS_KEY = "b60d03b2449869ee1a4ba331011a32e50475f820";

  private RedmineRepository myRepository;

  public void testIssueFilteringByStatus() throws Exception {
    // TODO: so far supplied query is unused, investigate

    // with closed issues
    Task[] found = myRepository.getIssues(null, 0, 25, true);
    assertEquals(6, found.length);

    // without closed issues
    found = myRepository.getIssues(null, 0, 25, false);
    assertEquals(5, found.length);

    // unique summary
    //found = myRepository.getIssues("baz", 0, 25, true);
    //assertEquals(1, found.length);
  }

  // IDEA-132015
  public void testIssueFilteringByAssignee() throws Exception {
    myRepository.setAssignedToMe(true);
    Task[] found = myRepository.getIssues(null, 0, 25, false);
    assertEquals(5, found.length);

    myRepository.setAssignedToMe(false);
    found = myRepository.getIssues(null, 0, 25, false);
    assertTrue(found.length > 5);
  }

  public void testIssueSearch() throws Exception {
    Task found = myRepository.findTask("7");
    assertNotNull(found);
    assertEquals("Summary contains 'baz'", found.getSummary());
  }

  public void testPagination() throws Exception {
    // all issues assigned to Integration Test User
    Task[] found = myRepository.getIssues("", 0, 20, true);
    assertTrue(found.length > 3);

    found = myRepository.getIssues("", 10, 20, true);
    assertEquals(0, found.length);

    found = myRepository.getIssues("", 0, 1, true);
    assertEquals(1, found.length);
  }

  public void testCommitMessageFormat() throws Exception {
    myRepository.setCommitMessageFormat("{project} {number} {id} {summary}");
    myRepository.setShouldFormatCommitMessage(true);
    LocalTaskImpl localTask = new LocalTaskImpl(myRepository.findTask(String.valueOf(7)));
    assertEquals("prj-1 7 7 Summary contains 'baz'", TaskUtil.getChangeListComment(localTask));
  }

  // IDEA-122845
  // Redmine doesn't send 401 or 403 errors, when issues are downloaded with wrong credentials (and anonymous access is allowed),
  // so current user information is fetched instead.
  public void testCredentialsCheck() {
    myRepository.setPassword("wrong-password");
    try {
      //noinspection ConstantConditions
      final Exception exception = myRepository.createCancellableConnection().call();
      assertNotNull("Test connection must fail when wrong credentials are specified", exception);
    }
    catch (Exception e) {
      assertEquals(TaskBundle.message("failure.login"), e.getMessage());
    }
  }

  // IDEA-138740
  public void testProjectSpecificUrlCheck() {
    myRepository.setUrl(REDMINE_2_0_TEST_SERVER_URL + "/projects/prj-1");
    try {
      //noinspection ConstantConditions
      final Exception exception = myRepository.createCancellableConnection().call();
      assertNotNull("Test connection must fail when project-specific URL is specified", exception);
    }
    catch (Exception e) {
      assertEquals(TaskBundle.message("failure.login"), e.getMessage());
    }
  }

  // IDEA-126470
  public void testIssueWithMissingDescription() throws Exception {
    final Task issue = myRepository.findTask("8");
    assertNotNull(issue);
    assertNull(issue.getDescription());
    assertEquals(issue.getSummary(), "Artificial issue with no description created via REST API. Do not update it!");
  }

  public void testIssueFilteringByProject() throws Exception {
    final List<RedmineProject> allProjects = myRepository.fetchProjects();
    final RedmineProject project = ContainerUtil.find(allProjects, project1 -> project1.getName().equals("With-Minus"));
    assertNotNull(project);
    myRepository.setCurrentProject(project);
    final Task[] issues = myRepository.getIssues("", 0, 10, false);
    assertNotNull(issues);
    assertEquals(1, issues.length);
    assertEquals(issues[0].getSummary(), "This issue was created for project filtering tests. Do not change it.");
  }

  // ZD-611794
  public void testSingleIssueRequestedWithApiKey() throws Exception {
    myRepository.setUseHttpAuthentication(false);
    myRepository.setAPIKey(API_ACCESS_KEY);
    try {
      assertNotNull(myRepository.findTask("1"));
    }
    finally {
      myRepository.setAPIKey("");
      myRepository.setUseHttpAuthentication(true);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new RedmineRepository(new RedmineRepositoryType());
    myRepository.setUrl(REDMINE_2_0_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
    myRepository.setUseHttpAuthentication(true);
  }
}


