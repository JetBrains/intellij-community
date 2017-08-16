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
import com.intellij.openapi.util.Couple;
import com.intellij.tasks.*;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRepositoryType;
import com.intellij.tasks.jira.JiraVersion;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.xmlrpc.CommonsXmlRpcTransport;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcRequest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.*;

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
  @NonNls private static final String JIRA_4_TEST_SERVER_URL = "http://idea-qa-task-2.labs.intellij.net:8014";

  /**
   * JIRA 5.0.6, REST API 2.0
   */
  @NonNls private static final String JIRA_5_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8015";

  private JiraRepository myRepository;

  public void testGerman() throws Exception {
    myRepository.setUsername("german");
    myRepository.setPassword("german");
    final Task[] issues = myRepository.getIssues(null, 50, 0);
    assertEquals(3, issues.length);
    assertEquals(TaskState.OPEN, issues[0].getState());
    assertFalse(issues[0].isClosed());
  }

  public void testLogin() {
    myRepository.setUsername("german");
    myRepository.setUsername("wrong password");
    //noinspection ConstantConditions
    final Exception exception = myRepository.createCancellableConnection().call();
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
    final Task[] found = myRepository.getIssues("UT-6", 0, 1, true);
    assertEquals(1, found.length);
    assertEquals("Summary contains 'bar'", found[0].getSummary());
  }

  /**
   * Holds only for JIRA > 5.x.x
   */
  public void testExtractedErrorMessage() {
    myRepository.setSearchQuery("foo < bar");
    try {
      myRepository.getIssues("", 50, 0);
      fail();
    }
    catch (Exception e) {
      assertEquals("Request failed. Reason: \"Field 'foo' does not exist or you do not have permission to view it.\"", e.getMessage());
    }
  }

  // Our test servers poorly handles frequent state updates of single dedicated issue. As a workaround
  // we create new issue for every test run (via XML-RPC API in JIRA 4.x and REST API in JIRA 5+)

  public void testSetTaskStateInJira5() throws Exception {
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    final String id = createIssueViaRestApi("BTSU", "Test issue for state updates (" + SHORT_TIMESTAMP_FORMAT.format(new Date()) + ")");
    changeTaskStateAndCheck(id);
  }

  // We can use XML-RPC in JIRA 5+ too, but nonetheless it's useful to have REST-based implementation as well
  private String createIssueViaRestApi(@NotNull String project, @NotNull String summary) throws Exception {
    final HttpClient client = myRepository.getHttpClient();
    final PostMethod method = new PostMethod(myRepository.getUrl() + "/rest/api/latest/issue");
    try {
      // For simplicity assume that project, summary and username don't contain illegal characters
      @Language("JSON")
      final String json = "{\"fields\": {\n" +
                          "  \"project\": {\n" +
                          "    \"key\": \"" + project + "\"\n" +
                          "  },\n" +
                          "  \"issuetype\": {\n" +
                          "    \"name\": \"Bug\"\n" +
                          "  },\n" +
                          "  \"assignee\": {\n" +
                          "    \"name\": \"" + myRepository.getUsername() + "\"\n" +
                          "  },\n" +
                          "  \"summary\": \"" + summary + "\"\n" +
                          "}}";
      method.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
      client.executeMethod(method);
      return new Gson().fromJson(method.getResponseBodyAsString(), JsonObject.class).get("id").getAsString();
    }
    finally {
      method.releaseConnection();
    }
  }

  public void testSetTaskStateInJira4() throws Exception {
    myRepository.setUrl(JIRA_4_TEST_SERVER_URL);
    final String id = createIssueViaXmlRpc("BTSU", "Test issue for state updates (" + SHORT_TIMESTAMP_FORMAT.format(new Date()) + ")");
    changeTaskStateAndCheck(id);
  }

  @SuppressWarnings("UseOfObsoleteCollectionType")
  @NotNull
  private String createIssueViaXmlRpc(@NotNull String project, @NotNull String summary) throws Exception {
    final URL url = new URL(myRepository.getUrl() + "/rpc/xmlrpc");
    final XmlRpcClient xmlRpcClient = new XmlRpcClient(url);
    final Map<String, Object> issue = new Hashtable<>();
    issue.put("summary", summary);
    issue.put("project", project);
    issue.put("assignee", myRepository.getUsername());
    issue.put("type", 1); // Bug
    issue.put("state", 1); // Open

    final Vector<Object> params = new Vector<>(Arrays.asList("", issue)); // empty token because of HTTP basic auth
    final Hashtable result = (Hashtable)xmlRpcClient.execute(new XmlRpcRequest("jira1.createIssue", params),
                                                             new CommonsXmlRpcTransport(url, myRepository.getHttpClient()));
    return (String)result.get("key");
  }

  private void changeTaskStateAndCheck(@NotNull String issueKey) throws Exception {
    final Task original = myRepository.findTask(issueKey);
    assertNotNull(original);
    myRepository.setTaskState(original, new CustomTaskState("4", "In Progress"));
    final Task updated = myRepository.findTask(issueKey);
    assertNotNull(updated);
    assertEquals(TaskState.IN_PROGRESS, updated.getState());
  }

  public void testSetTimeSpend() throws Exception {
    // only REST API 2.0 supports this feature
    myRepository.setUrl(JIRA_5_TEST_SERVER_URL);
    final String issueId = createIssueViaRestApi("BTTTU", "Test issue for time tracking updates (" + SHORT_TIMESTAMP_FORMAT.format(new Date()) + ")");
    final Task task = myRepository.findTask(issueId);
    assertNotNull("Test task not found", task);

    // timestamp as comment
    final String comment = "Timestamp: " + TaskUtil.formatDate(new Date());
    final Couple<Integer> duration = generateWorkItemDuration();

    final int hours = duration.getFirst(), minutes = duration.getSecond();
    myRepository.updateTimeSpent(new LocalTaskImpl(task), String.format("%dh %dm", hours, minutes), comment);

    final GetMethod request = new GetMethod(myRepository.getRestUrl("issue", task.getId(), "worklog"));
    final String response = myRepository.executeMethod(request);
    final JsonObject object = new Gson().fromJson(response, JsonObject.class);
    final JsonArray worklogs = object.get("worklogs").getAsJsonArray();
    final JsonObject last = worklogs.get(worklogs.size() - 1).getAsJsonObject();

    assertEquals(comment, last.get("comment").getAsString());
    // don't depend on concrete response format: zero hours stripping, zero padding and so on
    assertEquals((hours * 60 + minutes) * 60, last.get("timeSpentSeconds").getAsInt());
  }

  public void testParseVersionNumbers() {
    assertEquals("6.1.9", new JiraVersion("6.1-OD-09-WN").toString());
    assertEquals("5.0.6", new JiraVersion("5.0.6").toString());
    assertEquals("4.4.5", new JiraVersion("4.4.5").toString());
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
