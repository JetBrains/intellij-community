package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.tasks.TaskTestUtil.TaskBuilder;
import static com.intellij.tasks.TaskTestUtil.assertTasksEqual;

/**
 * @author Mikhail Golubev
 */
public class SprintlyIntegrationTest extends GenericSubtypeTestCase {
  private static final String TASK_LIST_RESPONSE = """
    [
       {
          "number" : 1,
          "progress" : {
             "started_at" : "2013-09-03T16:12:51+00:00",
             "triaged_at" : "2013-09-03T16:12:35+00:00"
          },
          "status" : "in-progress",
          "last_modified" : "2013-09-03T16:12:51+00:00",
          "score" : "~",
          "description" : "These SEGFAULTs oppress me",
          "tags" : [
             "bar",
             "tag",
             "foo"
          ],
          "email" : {
             "files" : "files-505027@items.sprint.ly",
             "discussion" : "discussion-505027@items.sprint.ly"
          },
          "created_at" : "2013-09-03T16:10:43+00:00",
          "assigned_to" : {
             "last_login" : "2013-09-03T16:02:32+00:00",
             "email" : "qsolo825@gmail.com",
             "created_at" : "2013-09-03T16:02:30+00:00",
             "id" : 17259,
             "last_name" : "Golubev",
             "first_name" : "Mikhail"
          },
          "type" : "defect",
          "product" : {
             "name" : "My Enormous Project",
             "id" : 14182,
             "archived" : false
          },
          "title" : "Nasty Bug!",
          "short_url" : "http://sprint.ly/i/14182/1/",
          "created_by" : {
             "last_login" : "2013-09-03T16:02:32+00:00",
             "email" : "qsolo825@gmail.com",
             "created_at" : "2013-09-03T16:02:30+00:00",
             "id" : 17259,
             "last_name" : "Golubev",
             "first_name" : "Mikhail"
          }
       },
       {
          "number" : 2,
          "progress" : {
             "triaged_at" : "2013-09-03T16:14:08+00:00"
          },
          "status" : "backlog",
          "last_modified" : "2013-09-04T10:18:02+00:00",
          "score" : "~",
          "description" : "Finish Sprint.ly connector",
          "tags" : [
             "foo"
          ],
          "email" : {
             "files" : "files-505035@items.sprint.ly",
             "discussion" : "discussion-505035@items.sprint.ly"
          },
          "created_at" : "2013-09-03T16:13:54+00:00",
          "assigned_to" : {
             "last_login" : "2013-09-03T16:02:32+00:00",
             "email" : "qsolo825@gmail.com",
             "created_at" : "2013-09-03T16:02:30+00:00",
             "id" : 17259,
             "last_name" : "Golubev",
             "first_name" : "Mikhail"
          },
          "type" : "task",
          "product" : {
             "name" : "My Enormous Project",
             "id" : 14182,
             "archived" : false
          },
          "title" : "Some task",
          "short_url" : "http://sprint.ly/i/14182/2/",
          "created_by" : {
             "last_login" : "2013-09-03T16:02:32+00:00",
             "email" : "qsolo825@gmail.com",
             "created_at" : "2013-09-03T16:02:30+00:00",
             "id" : 17259,
             "last_name" : "Golubev",
             "first_name" : "Mikhail"
          }
       }
    ]
    """;

  public static final String SINGLE_TASK_RESPONSE = """
    {
       "number" : 1,
       "progress" : {
          "started_at" : "2013-09-03T16:12:51+00:00",
          "triaged_at" : "2013-09-03T16:12:35+00:00"
       },
       "status" : "in-progress",
       "last_modified" : "2013-09-04T13:53:14+00:00",
       "score" : "~",
       "description" : "This is this horrible bug's description",
       "tags" : [
          "bar",
          "tag",
          "foo"
       ],
       "email" : {
          "files" : "files-505027@items.sprint.ly",
          "discussion" : "discussion-505027@items.sprint.ly"
       },
       "created_at" : "2013-09-03T16:10:43+00:00",
       "assigned_to" : {
          "last_login" : "2013-09-03T16:02:32+00:00",
          "email" : "qsolo825@gmail.com",
          "created_at" : "2013-09-03T16:02:30+00:00",
          "id" : 17259,
          "last_name" : "Golubev",
          "first_name" : "Mikhail"
       },
       "type" : "defect",
       "product" : {
          "name" : "My Enormous Project",
          "id" : 14182,
          "archived" : false
       },
       "title" : "Nasty Bug!",
       "short_url" : "http://sprint.ly/i/14182/1/",
       "created_by" : {
          "last_login" : "2013-09-03T16:02:32+00:00",
          "email" : "qsolo825@gmail.com",
          "created_at" : "2013-09-03T16:02:30+00:00",
          "id" : 17259,
          "last_name" : "Golubev",
          "first_name" : "Mikhail"
       }
    }
    """;

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
