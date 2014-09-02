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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRepositoryType;
import com.intellij.tasks.jira.JiraVersion;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NonNls;

import java.util.Date;

import static com.intellij.tasks.jira.JiraRemoteApi.ApiType.REST_2_0;
import static com.intellij.tasks.jira.JiraRemoteApi.ApiType.REST_2_0_ALPHA;

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
    //noinspection ConstantConditions
    Exception exception = myRepository.createCancellableConnection().call();
    assertNotNull(exception);
    assertEquals(TaskBundle.message("failure.login"), exception.getMessage());
  }

  public void testVersionDiscovery() throws Exception {
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    assertEquals(REST_2_0, myRepository.discoverApiVersion().getType());
    myRepository.setUrl(JIRA_4_TEST_SERVER_URL);
    assertEquals(REST_2_0_ALPHA, myRepository.discoverApiVersion().getType());
  }

  public void testJqlQuery() throws Exception {
    myRepository.setSearchQuery("assignee = currentUser() AND (summary ~ 'foo' or resolution = Fixed)");
    assertEquals(2, myRepository.getIssues("", 50, 0).length);

    // test that user part of query is prepended to existing one
    myRepository.setSearchQuery("assignee = currentUser() order by updated");
    assertEquals(1, myRepository.getIssues("foo", 50, 0).length);
  }

  /**
   * Should return null, not throw exceptions by contact.
   */
  public void testIssueNotExists() throws Exception {
    assertNull(myRepository.findTask("FOO-42"));
  }

  /**
   * If query string looks like task ID, separate request will be made to download issue.
   */
  public void testFindSingleIssue() throws Exception {
    Task[] found = myRepository.getIssues("UT-6", 0, 1, true);
    assertEquals(1, found.length);
    assertEquals("Summary contains 'bar'", found[0].getSummary());
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

  // TODO move to on-Demand-specific tests
  //public void testBasicAuthenticationDisabling() throws Exception {
  //  assertTrue("Basic authentication should be enabled at first", myRepository.isUseHttpAuthentication());
  //  myRepository.findTask("PRJONE-1");
  //  assertFalse("Basic authentication should be disabled once JSESSIONID cookie was received", myRepository.isUseHttpAuthentication());
  //  HttpClient client = myRepository.getHttpClient();
  //  assertFalse(client.getParams().isAuthenticationPreemptive());
  //  assertNull(client.getState().getCredentials(AuthScope.ANY));
  //}

  public void testSetTaskState() throws Exception {
    changeStateAndCheck(JIRA_4_TEST_SERVER_URL, "PRJONE-8");
    changeStateAndCheck(JIRA_5_TEST_SERVER_URL, "UT-8");
  }

  public void testSetTimeSpend() throws Exception {
    // only REST API 2.0 supports this feature
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    Task task = myRepository.findTask("UT-9");
    assertNotNull("Test task not found", task);

    // timestamp as comment
    String comment = "Timestamp: " + TaskUtil.formatDate(new Date());

    // semi-unique duration as timeSpend
    // should be no longer than 8 hours in total, because it's considered as one full day
    int minutes = (int)(System.currentTimeMillis() % 240) + 1;
    String duration = String.format("%dh %dm", minutes / 60, minutes % 60);
    myRepository.updateTimeSpent(new LocalTaskImpl(task), duration, comment);

    // possible race conditions?
    GetMethod request = new GetMethod(myRepository.getRestUrl("issue", task.getId(), "worklog"));
    String response = myRepository.executeMethod(request);
    JsonObject object = new Gson().fromJson(response, JsonObject.class);
    JsonArray worklogs = object.get("worklogs").getAsJsonArray();
    JsonObject last = worklogs.get(worklogs.size() - 1).getAsJsonObject();

    assertEquals(comment, last.get("comment").getAsString());
    // don't depend on concrete response format: zero hours stripping, zero padding and so on
    assertEquals(minutes * 60, last.get("timeSpentSeconds").getAsInt());
  }

  private void changeStateAndCheck(String url, String key) throws Exception {
    myRepository.setUrl(url);
    Task task = myRepository.findTask(key);
    assertNotNull("Test task not found", task);
    // set required initial state, if was left wrong
    if (task.getState() != TaskState.REOPENED) {
      myRepository.setTaskState(task, TaskState.REOPENED);
    }
    try {
      //assertEquals("Wrong initial state of test issue: " + key, TaskState.REOPENED, task.getState());
      myRepository.setTaskState(task, TaskState.RESOLVED);
      task = myRepository.findTask(key);
      assertEquals(task.getState(), TaskState.RESOLVED);
    }
    finally {
      try {
        // always attempt to restore original state of the issue
        myRepository.setTaskState(task, TaskState.REOPENED);
      }
      catch (Exception ignored) {
        // empty
      }
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
    TaskSettings.getInstance().CONNECTION_TIMEOUT = 20000;
    myRepository = new JiraRepository(new JiraRepositoryType());
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
  }
}
