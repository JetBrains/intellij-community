package com.intellij.tasks.integration;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public class YouTrackIntegrationTest extends TaskManagerTestCase {
  private static final String YOUTRACK_4_TEST_SERVER_URL = "http://trackers-tests.labs.intellij.net:8067";

  private static final String REQUEST_WITH_CUSTOM_STATES_ID = "YT4CS-1";
  private static final CustomTaskState NORTH_STATE = new CustomTaskState("North", "North");
  private static final CustomTaskState SUBMITTED_STATE = new CustomTaskState("Submitted", "Submitted");

  private YouTrackRepository myRepository;

  public void testCustomTaskStates() throws Exception {
    final Task task = myRepository.findTask(REQUEST_WITH_CUSTOM_STATES_ID);
    assertNotNull(task);

    final Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
    final List<String> stateNames = ContainerUtil.map(states, new Function<CustomTaskState, String>() {
      @Override
      public String fun(CustomTaskState state) {
        return state.getPresentableName();
      }
    });
    assertContainsElements(stateNames, "North", "South");

    // ? -> North
    myRepository.setTaskState(task, NORTH_STATE);
    Element element = myRepository.fetchRequestAsElement(REQUEST_WITH_CUSTOM_STATES_ID);
    assertEquals("North", element.getAttributeValue("state"));

    // North -> Submitted
    myRepository.setTaskState(task, SUBMITTED_STATE);
    element = myRepository.fetchRequestAsElement(REQUEST_WITH_CUSTOM_STATES_ID);
    assertEquals("Submitted", element.getAttributeValue("state"));
  }

  // IDEA-101238
  public void testTimeTracking() throws Exception {
    final HttpClient client = myRepository.getHttpClient();
    authenticate(client);
    final String issueId = createIssue(client);
    final Task task = myRepository.findTask(issueId);
    assertNotNull(task);
    final Couple<Integer> duration = generateWorkItemDuration();
    final String spentTime = formatDuration(duration.getFirst(), duration.getSecond());
    myRepository.updateTimeSpent(new LocalTaskImpl(task), spentTime, "Foo Bar");
    checkSpentTime(client, issueId, spentTime);
  }

  @NotNull
  private static String formatDuration(int hours, int minutes) {
    final String spentTime;
    if (hours == 0) {
      spentTime = minutes + "m";
    }
    else if (minutes == 0) {
      spentTime = hours + "h";
    }
    else {
      spentTime = String.format("%dh%dm", hours, minutes);
    }
    return spentTime;
  }

  private void authenticate(@NotNull HttpClient client) throws IOException {
    PostMethod method = new PostMethod(myRepository.getUrl() + "/rest/user/login");
    method.addParameter("login", myRepository.getUsername());
    method.addParameter("password", myRepository.getPassword());
    final int statusCode = client.executeMethod(method);
    assertEquals(HttpStatus.SC_OK, statusCode);
  }

  @NotNull
  private String createIssue(@NotNull HttpClient client) throws IOException {
    // http PUT "http://trackers-tests.labs.intellij.net:8067/rest/issue" project==BTYT4TT "summary==First issue created via REST API" 
    final PutMethod method = new PutMethod(myRepository.getUrl() + "/rest/issue");
    method.setQueryString(new NameValuePair[] {
      new NameValuePair("project", "BTYT4TT"),
      new NameValuePair("summary", "Test issue for time tracking updates (" + SHORT_TIMESTAMP_FORMAT.format(new Date()) + ")")
    });
    final int statusCode = client.executeMethod(method);
    assertEquals(HttpStatus.SC_CREATED, statusCode);
    final Header locationHeader = method.getResponseHeader("Location");
    assertNotNull(locationHeader);
    // Otherwise there will be timeout on connection acquiring
    method.releaseConnection();
    return PathUtil.getFileName(locationHeader.getValue());
  }

  private void checkSpentTime(@NotNull HttpClient client, @NotNull String issueId, @NotNull String expectedTime) throws IOException, JDOMException {
    // Endpoint /rest/issue/BTYT4TT-8/timetracking/workitem/ doesn't work on this instance of YouTrack for some reason
    final GetMethod method = new GetMethod(myRepository.getUrl() + "/rest/issue/" + issueId);
    final int statusCode = client.executeMethod(method);
    assertEquals(HttpStatus.SC_OK, statusCode);
    final Element root = JDOMUtil.load(method.getResponseBodyAsStream());
    for (Element field : root.getChildren("field")) {
      if ("Spent time".equals(field.getAttributeValue("name"))) {
        final Element value = field.getChild("value");
        assertNotNull(value);
        assertEquals(expectedTime, value.getText().trim());
        return;
      }
    }
    fail("Field 'Spent time' not found in issue " + issueId);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = new YouTrackRepository(new YouTrackRepositoryType());
    myRepository.setUrl(YOUTRACK_4_TEST_SERVER_URL);
    myRepository.setUsername("buildtest");
    myRepository.setPassword("buildtest");
  }
}
