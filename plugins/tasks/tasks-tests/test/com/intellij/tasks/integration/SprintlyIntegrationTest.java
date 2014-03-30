package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskTestUtil;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.tasks.TaskTestUtil.TaskBuilder;
import static com.intellij.tasks.TaskTestUtil.assertTasksEqual;

/**
 * @author Mikhail Golubev
 */
public class SprintlyIntegrationTest extends GenericSubtypeTestCase {
  private static final String TASK_LIST_RESPONSE = "[\n" +
                                                   "   {\n" +
                                                   "      \"number\" : 1,\n" +
                                                   "      \"progress\" : {\n" +
                                                   "         \"started_at\" : \"2013-09-03T16:12:51+00:00\",\n" +
                                                   "         \"triaged_at\" : \"2013-09-03T16:12:35+00:00\"\n" +
                                                   "      },\n" +
                                                   "      \"status\" : \"in-progress\",\n" +
                                                   "      \"last_modified\" : \"2013-09-03T16:12:51+00:00\",\n" +
                                                   "      \"score\" : \"~\",\n" +
                                                   "      \"description\" : \"These SEGFAULTs oppress me\",\n" +
                                                   "      \"tags\" : [\n" +
                                                   "         \"bar\",\n" +
                                                   "         \"tag\",\n" +
                                                   "         \"foo\"\n" +
                                                   "      ],\n" +
                                                   "      \"email\" : {\n" +
                                                   "         \"files\" : \"files-505027@items.sprint.ly\",\n" +
                                                   "         \"discussion\" : \"discussion-505027@items.sprint.ly\"\n" +
                                                   "      },\n" +
                                                   "      \"created_at\" : \"2013-09-03T16:10:43+00:00\",\n" +
                                                   "      \"assigned_to\" : {\n" +
                                                   "         \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                   "         \"email\" : \"qsolo825@gmail.com\",\n" +
                                                   "         \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                   "         \"id\" : 17259,\n" +
                                                   "         \"last_name\" : \"Golubev\",\n" +
                                                   "         \"first_name\" : \"Mikhail\"\n" +
                                                   "      },\n" +
                                                   "      \"type\" : \"defect\",\n" +
                                                   "      \"product\" : {\n" +
                                                   "         \"name\" : \"My Enormous Project\",\n" +
                                                   "         \"id\" : 14182,\n" +
                                                   "         \"archived\" : false\n" +
                                                   "      },\n" +
                                                   "      \"title\" : \"Nasty Bug!\",\n" +
                                                   "      \"short_url\" : \"http://sprint.ly/i/14182/1/\",\n" +
                                                   "      \"created_by\" : {\n" +
                                                   "         \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                   "         \"email\" : \"qsolo825@gmail.com\",\n" +
                                                   "         \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                   "         \"id\" : 17259,\n" +
                                                   "         \"last_name\" : \"Golubev\",\n" +
                                                   "         \"first_name\" : \"Mikhail\"\n" +
                                                   "      }\n" +
                                                   "   },\n" +
                                                   "   {\n" +
                                                   "      \"number\" : 2,\n" +
                                                   "      \"progress\" : {\n" +
                                                   "         \"triaged_at\" : \"2013-09-03T16:14:08+00:00\"\n" +
                                                   "      },\n" +
                                                   "      \"status\" : \"backlog\",\n" +
                                                   "      \"last_modified\" : \"2013-09-04T10:18:02+00:00\",\n" +
                                                   "      \"score\" : \"~\",\n" +
                                                   "      \"description\" : \"Finish Sprint.ly connector\",\n" +
                                                   "      \"tags\" : [\n" +
                                                   "         \"foo\"\n" +
                                                   "      ],\n" +
                                                   "      \"email\" : {\n" +
                                                   "         \"files\" : \"files-505035@items.sprint.ly\",\n" +
                                                   "         \"discussion\" : \"discussion-505035@items.sprint.ly\"\n" +
                                                   "      },\n" +
                                                   "      \"created_at\" : \"2013-09-03T16:13:54+00:00\",\n" +
                                                   "      \"assigned_to\" : {\n" +
                                                   "         \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                   "         \"email\" : \"qsolo825@gmail.com\",\n" +
                                                   "         \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                   "         \"id\" : 17259,\n" +
                                                   "         \"last_name\" : \"Golubev\",\n" +
                                                   "         \"first_name\" : \"Mikhail\"\n" +
                                                   "      },\n" +
                                                   "      \"type\" : \"task\",\n" +
                                                   "      \"product\" : {\n" +
                                                   "         \"name\" : \"My Enormous Project\",\n" +
                                                   "         \"id\" : 14182,\n" +
                                                   "         \"archived\" : false\n" +
                                                   "      },\n" +
                                                   "      \"title\" : \"Some task\",\n" +
                                                   "      \"short_url\" : \"http://sprint.ly/i/14182/2/\",\n" +
                                                   "      \"created_by\" : {\n" +
                                                   "         \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                   "         \"email\" : \"qsolo825@gmail.com\",\n" +
                                                   "         \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                   "         \"id\" : 17259,\n" +
                                                   "         \"last_name\" : \"Golubev\",\n" +
                                                   "         \"first_name\" : \"Mikhail\"\n" +
                                                   "      }\n" +
                                                   "   }\n" +
                                                   "]\n";

  public static final String SINGLE_TASK_RESPONSE = "{\n" +
                                                    "   \"number\" : 1,\n" +
                                                    "   \"progress\" : {\n" +
                                                    "      \"started_at\" : \"2013-09-03T16:12:51+00:00\",\n" +
                                                    "      \"triaged_at\" : \"2013-09-03T16:12:35+00:00\"\n" +
                                                    "   },\n" +
                                                    "   \"status\" : \"in-progress\",\n" +
                                                    "   \"last_modified\" : \"2013-09-04T13:53:14+00:00\",\n" +
                                                    "   \"score\" : \"~\",\n" +
                                                    "   \"description\" : \"This is this horrible bug's description\",\n" +
                                                    "   \"tags\" : [\n" +
                                                    "      \"bar\",\n" +
                                                    "      \"tag\",\n" +
                                                    "      \"foo\"\n" +
                                                    "   ],\n" +
                                                    "   \"email\" : {\n" +
                                                    "      \"files\" : \"files-505027@items.sprint.ly\",\n" +
                                                    "      \"discussion\" : \"discussion-505027@items.sprint.ly\"\n" +
                                                    "   },\n" +
                                                    "   \"created_at\" : \"2013-09-03T16:10:43+00:00\",\n" +
                                                    "   \"assigned_to\" : {\n" +
                                                    "      \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                    "      \"email\" : \"qsolo825@gmail.com\",\n" +
                                                    "      \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                    "      \"id\" : 17259,\n" +
                                                    "      \"last_name\" : \"Golubev\",\n" +
                                                    "      \"first_name\" : \"Mikhail\"\n" +
                                                    "   },\n" +
                                                    "   \"type\" : \"defect\",\n" +
                                                    "   \"product\" : {\n" +
                                                    "      \"name\" : \"My Enormous Project\",\n" +
                                                    "      \"id\" : 14182,\n" +
                                                    "      \"archived\" : false\n" +
                                                    "   },\n" +
                                                    "   \"title\" : \"Nasty Bug!\",\n" +
                                                    "   \"short_url\" : \"http://sprint.ly/i/14182/1/\",\n" +
                                                    "   \"created_by\" : {\n" +
                                                    "      \"last_login\" : \"2013-09-03T16:02:32+00:00\",\n" +
                                                    "      \"email\" : \"qsolo825@gmail.com\",\n" +
                                                    "      \"created_at\" : \"2013-09-03T16:02:30+00:00\",\n" +
                                                    "      \"id\" : 17259,\n" +
                                                    "      \"last_name\" : \"Golubev\",\n" +
                                                    "      \"first_name\" : \"Mikhail\"\n" +
                                                    "   }\n" +
                                                    "}\n";

  public void testParsingTaskList() throws Exception {
    Task[] tasks = myRepository.getActiveResponseHandler().parseIssues(TASK_LIST_RESPONSE, 50);
    assertTasksEqual(new Task[]{
      new TaskBuilder("1", "Nasty Bug!", myRepository)
        .withDescription("These SEGFAULTs oppress me")
        .withIssueUrl("http://sprint.ly/i/14182/1/")
        .withUpdated("2013-09-03 16:12:51")
        .withCreated("2013-09-03 16:10:43"),
      new TaskBuilder("2", "Some task", myRepository)
        .withDescription("Finish Sprint.ly connector")
        .withIssueUrl("http://sprint.ly/i/14182/2/")
        .withUpdated("2013-09-04 10:18:02")
        .withCreated("2013-09-03 16:13:54")
    }, tasks);
  }

  public void testParsingSingleTask() throws Exception {
    Task task = myRepository.getActiveResponseHandler().parseIssue(SINGLE_TASK_RESPONSE);
    assertTasksEqual(
      new TaskBuilder("1", "Nasty Bug!", myRepository)
        .withDescription("This is this horrible bug's description")
        .withIssueUrl("http://sprint.ly/i/14182/1/")
        .withUpdated("2013-09-04 13:53:14")
        .withCreated("2013-09-03 16:10:43"),
      task);
  }

  @NotNull
  @Override
  protected GenericRepository createRepository(GenericRepositoryType genericType) {
    return (GenericRepository)genericType.new SprintlyRepository().createRepository();
  }
}
