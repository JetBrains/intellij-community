/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRepositoryType;
import com.intellij.tasks.jira.JiraVersion;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 *         Date: 1/15/13
 */
public class JiraIntegrationTest extends TaskManagerTestCase {

  /**
   * JIRA 4.4.5, REST API 2.0.alpha1
   */
  @NonNls private static final String JIRA_4_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8014";
  /**
   * JIRA 5.0.6, REST API 2.0
   */
  @NonNls private static final String JIRA_5_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8015";

  private JiraRepository myRepository;

  public void testGerman() throws Exception {
    myRepository.setUsername("german");
    myRepository.setPassword("german");
    Task[] issues = myRepository.getIssues(null, 50, 0);
    assertEquals(3, issues.length);
    assertEquals(TaskState.OPEN, issues[0].getState());
    assertFalse(issues[0].isClosed());
  }

  public void testLogin() throws Exception {
    myRepository.setUsername("german");
    myRepository.setUsername("wrong password");
    Exception exception = myRepository.createCancellableConnection().call();
    assertNotNull(exception);
    assertEquals(JiraRepository.LOGIN_FAILED_CHECK_YOUR_PERMISSIONS, exception.getMessage());
  }

  public void testVersionDiscovery() throws Exception {
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    assertEquals("2.0", myRepository.discoverRestApiVersion().getVersionName());
    myRepository.setUrl(JIRA_4_TEST_SERVER_URL);
    assertEquals("2.0.alpha1", myRepository.discoverRestApiVersion().getVersionName());
  }

  public void testJqlQuery() throws Exception {
    myRepository.setSearchQuery("assignee = currentUser() AND (summary ~ 'foo' or resolution = Fixed)");
    assertEquals(2, myRepository.getIssues("", 50, 0).length);
  }

  /**
   * Holds only for JIRA > 5.x.x
   */
  public void testExtractedErrorMessage() throws Exception {
    myRepository.setSearchQuery("foo < bar");
    try {
      myRepository.getIssues("", 50, 0);
      fail();
    }
    catch (Exception e) {
      assertEquals("Request failed. Reason: \"Field 'foo' does not exist or you do not have permission to view it.\"", e.getMessage());
    }
  }

  public void testSetTaskState() throws Exception {
    changeStateAndCheck(JIRA_4_TEST_SERVER_URL, "PRJONE-8");
    changeStateAndCheck(JIRA_5_TEST_SERVER_URL, "UT-8");
  }

  private void changeStateAndCheck(String url, String key) throws Exception {
    myRepository.setUrl(url);
    Task task = myRepository.findTask(key);
    try {
      myRepository.setTaskState(task, TaskState.IN_PROGRESS);
      assertEquals(myRepository.findTask(key).getState(), TaskState.IN_PROGRESS);
      myRepository.setTaskState(task, TaskState.RESOLVED);
      assertEquals(myRepository.findTask(key).getState(), TaskState.RESOLVED);
      myRepository.setTaskState(task, TaskState.REOPENED);
      assertEquals(myRepository.findTask(key).getState(), TaskState.REOPENED);
    }
    catch (Exception e) {
      // always attempt to restore original state of the issue
      try {
        // transition to Resolved state usually is possible from any other
        myRepository.setTaskState(task, TaskState.RESOLVED);
      }
      catch (Exception ignored) {
      }
      try {
        myRepository.setTaskState(task, TaskState.REOPENED);
      }
      catch (Exception ignored) {
      }
      throw e;
    }
  }

  public void testParseVersionNumbers() throws Exception {
    assertEquals(new JiraVersion("6.1-OD-09-WN").toString(), "6.1.9");
    assertEquals(new JiraVersion("5.0.6").toString(), "5.0.6");
    assertEquals(new JiraVersion("4.4.5").toString(), "4.4.5");

  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new JiraRepository(new JiraRepositoryType());
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
  }
}
