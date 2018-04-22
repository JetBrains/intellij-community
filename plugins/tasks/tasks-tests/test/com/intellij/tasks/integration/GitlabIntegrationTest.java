package com.intellij.tasks.integration;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.Couple;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.gitlab.GitlabRepository;
import com.intellij.tasks.gitlab.GitlabTask;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Mikhail Golubev
 */
public class GitlabIntegrationTest extends TaskManagerTestCase {
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private static final String SERVER_URL = "http://trackers-tests.labs.intellij.net:8045";
  private GitlabRepository myRepository;

  public void testCommitMessageFormat() {
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
    assertEquals("project-1 2 #2 Sample title", changeListComment);

    myRepository.setProjects(Collections.emptyList());
    localTask = new LocalTaskImpl(new GitlabTask(myRepository, issue));
    changeListComment = TaskUtil.getChangeListComment(localTask);
    // Project is unknown, so "" is substituted instead
    assertEquals(" 2 #2 Sample title", changeListComment);
  }

  public void testIssueFilteringByState() throws Exception {
    final GitlabProject project = ContainerUtil.find(myRepository.getProjects(), p -> p.getName().equals("Issue Filtering Tests"));
    assertNotNull(project);
    myRepository.setCurrentProject(project);

    final Task[] allIssues = myRepository.getIssues("", 0, 20, true);
    assertSize(2, allIssues);
    assertNotNull(ContainerUtil.find(allIssues, task -> task.isClosed() && task.getSummary().equals("Closed issue #1")));
    assertNotNull(ContainerUtil.find(allIssues, task -> !task.isClosed() && task.getSummary().equals("Opened issue #1")));

    final Task[] openedIssues = myRepository.getIssues("", 0, 20, false);
    assertSize(1, openedIssues);
    assertFalse(openedIssues[0].isClosed());
    assertEquals("Opened issue #1", openedIssues[0].getSummary());
  }

  // IDEA-136499
  public void testPresentableId() throws Exception {
    final GitlabIssue issue = myRepository.fetchIssue(5 /* ID Formatting Tests */, 10);
    assertNotNull(issue);
    assertEquals(10, issue.getId());
    assertEquals(1, issue.getLocalId());
    assertEquals(5, issue.getProjectId());

    final GitlabTask task = new GitlabTask(myRepository, issue);
    assertEquals("#1", task.getPresentableId());
    assertEquals("1", task.getNumber());
    assertEquals("ID Formatting Tests", task.getProject());
    assertEquals("10", task.getId());
    assertEquals("#1: First issue with iid = 1", task.toString());
    myRepository.setShouldFormatCommitMessage(true);
    assertEquals("#1 First issue with iid = 1", myRepository.getTaskComment(task));
  }

  // IDEA-190710
  public void testTimeTracking() throws Exception {
    final int projectId = 5;
    final int issueId = 10;
    final GitlabIssue issue = myRepository.fetchIssue(projectId, issueId);
    assertNotNull(issue);
    assertEquals(issueId, issue.getId());
    assertEquals(1, issue.getLocalId());
    assertEquals(projectId, issue.getProjectId());

    final GitlabTask task = new GitlabTask(myRepository, issue);
    final LocalTaskImpl localTask = new LocalTaskImpl(task);

    //Reset spent time in case it isn't empty
    //If it doesn't have 0 time spent, the check later will fail
    final HttpPost resetMethod = new HttpPost(myRepository.getRestApiUrl("projects", projectId, "issues", issueId, "reset_spent_time"));
    assertEquals(200, myRepository.getHttpClient().execute(resetMethod).getStatusLine().getStatusCode());

    final Couple<Integer> time = generateWorkItemDuration();
    final String timeSpent = formatDuration(time.getFirst(), time.getSecond());
    myRepository.updateTimeSpent(localTask, timeSpent, "Unused");
    checkSpentTime(projectId, issueId, timeSpent);
  }

  private void checkSpentTime(@NotNull int projectId,
                              @NotNull int issueId,
                              @NotNull String timeSpent) throws IOException {

    // URL to check time spent http://example.gitlab.com/api/v3/projects/1/issues/1/time_stats
    final HttpGet method = new HttpGet(myRepository.getRestApiUrl("projects", projectId, "issues", issueId, "time_stats"));

    //Create a deserializer for the response
    TaskResponseUtil.GsonSingleObjectDeserializer<TimeResponse> handler = new TaskResponseUtil.GsonSingleObjectDeserializer<>(GSON, TimeResponse.class);
    TimeResponse response = myRepository.getHttpClient().execute(method, handler);
    assertNotNull(response);
    assertNotNull(response.timeSpent);

    //GitLab returns with a space between hours and minutes
    String returned = response.timeSpent.replace(" ", "");
    assertEquals(returned, timeSpent);
  }

  private static class TimeResponse {
    @SerializedName("human_total_time_spent")
    public String timeSpent;
    @SerializedName("human_time_estimate")
    public String timeEstimate;
  }

  //Copied from YouTrackIntegrationTest
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new GitlabRepository();
    myRepository.setUrl(SERVER_URL);
    myRepository.setPassword("PqbBxWaqFxZijQXKPLLo"); // buildtest
  }
}
