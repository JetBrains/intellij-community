package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.redmine.RedmineRepository;
import com.intellij.tasks.redmine.RedmineRepositoryType;

/**
 * @author Mikhail Golubev
 */
public class RedmineIntegrationTest extends TaskManagerTestCase {
  private static final String REDMINE_2_0_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8072/";

  private RedmineRepository myRepository;

  public void testIssueFiltering() throws Exception {
    // TODO: so far supplied query is unused, investigate

    // with closed issues
    Task[] found = myRepository.getIssues(null, 0, 25, true);
    assertEquals(4, found.length);

    // without closed issues
    found = myRepository.getIssues(null, 0, 25, false);
    assertEquals(3, found.length);

    // unique summary
    //found = myRepository.getIssues("baz", 0, 25, true);
    //assertEquals(1, found.length);
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


