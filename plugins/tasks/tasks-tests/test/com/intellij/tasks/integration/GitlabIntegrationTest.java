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
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.jetbrains.annotations.NotNull;

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

  private static class GitLabTimeStats {
    @SerializedName("time_estimate")
    private int timeEstimate;
    @SerializedName("total_time_spent")
    private int totalTimeSpent;
    @SerializedName("human_time_estimate")
    private String humanTimeEstimate;
    @SerializedName("human_total_time_spent")
    private String humanTotalTimeSpent;
  }

  // IDEA-190710
  public void testTimeTracking() throws Exception {
    final GitlabIssue issue = myRepository.fetchIssue(5, 10);
    assertNotNull(issue);
    assertEquals(1, issue.getLocalId());
    assertEquals(6153700, issue.getProjectId());

    final GitlabTask task = new GitlabTask(myRepository, issue);
    assertEquals("1", task.getNumber());

    final LocalTaskImpl localTask = new LocalTaskImpl(task);
    assertEquals("1", localTask.getNumber());

    // First unset any current time spent
    final HttpPost resetTimeRequest =
      new HttpPost(myRepository.getRestApiUrl("projects", issue.getProjectId(), "issues", issue.getLocalId(), "reset_spent_time"));
    final ResponseHandler<GitLabTimeStats> handler =
      new TaskResponseUtil.GsonSingleObjectDeserializer<>(myRepository.getGson(), GitLabTimeStats.class);
    final GitLabTimeStats resetTimeResponse = myRepository.getHttpClient().execute(resetTimeRequest, handler);
    assertEquals(0, resetTimeResponse.totalTimeSpent);

    // Then add time to ticket
    final Couple<Integer> duration = generateWorkItemDuration();
    myRepository.updateTimeSpent(localTask, formatDuration(duration.getFirst(), duration.getSecond()), ""); // Test without comment

    // And check that the time was added properly
    final HttpGet checkTimeRequest =
      new HttpGet(myRepository.getRestApiUrl("projects", issue.getProjectId(), "issues", issue.getLocalId(), "time_stats"));
    final GitLabTimeStats checkTimeResponse = myRepository.getHttpClient().execute(checkTimeRequest, handler);

    final int expectedTime = duration.getFirst() * 3600 + duration.getSecond() * 60;
    assertEquals(expectedTime, checkTimeResponse.totalTimeSpent);
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new GitlabRepository();
    myRepository.setUrl(SERVER_URL);
    myRepository.setPassword("PqbBxWaqFxZijQXKPLLo"); // buildtest
  }
}
