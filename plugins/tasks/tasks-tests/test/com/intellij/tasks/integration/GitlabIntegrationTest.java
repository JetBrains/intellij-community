package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskTestUtil;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class GitlabIntegrationTest extends GenericSubtypeTestCase {
  private static final String TASK_LIST_RESPONSE = "[\n" +
                                                  "  {\n" +
                                                  "    \"id\": 2,\n" +
                                                  "    \"iid\": 2,\n" +
                                                  "    \"project_id\": 1,\n" +
                                                  "    \"title\": \"Drink more tea\",\n" +
                                                  "    \"description\": \"It\\u0027s healthy.\",\n" +
                                                  "    \"labels\": [],\n" +
                                                  "    \"assignee\": {\n" +
                                                  "      \"id\": 4,\n" +
                                                  "      \"username\": \"deva\",\n" +
                                                  "      \"email\": \"deva@somemail.com\",\n" +
                                                  "      \"name\": \"John Smith\",\n" +
                                                  "      \"state\": \"active\",\n" +
                                                  "      \"created_at\": \"2013-11-14T12:34:37Z\"\n" +
                                                  "    },\n" +
                                                  "    \"author\": {\n" +
                                                  "      \"id\": 1,\n" +
                                                  "      \"username\": \"root\",\n" +
                                                  "      \"email\": \"admin@local.host\",\n" +
                                                  "      \"name\": \"Administrator\",\n" +
                                                  "      \"state\": \"active\",\n" +
                                                  "      \"created_at\": \"2013-11-14T12:19:43Z\"\n" +
                                                  "    },\n" +
                                                  "    \"state\": \"opened\",\n" +
                                                  "    \"updated_at\": \"2013-11-14T12:38:51Z\",\n" +
                                                  "    \"created_at\": \"2013-11-14T12:33:07Z\"\n" +
                                                  "  },\n" +
                                                  "  {\n" +
                                                  "    \"id\": 1,\n" +
                                                  "    \"iid\": 1,\n" +
                                                  "    \"project_id\": 1,\n" +
                                                  "    \"title\": \"Eat more bananas\",\n" +
                                                  "    \"description\": \"They're tasty.\",\n" +
                                                  "    \"labels\": [\n" +
                                                  "      \"spam\"\n" +
                                                  "    ],\n" +
                                                  "    \"assignee\": {\n" +
                                                  "      \"id\": 1,\n" +
                                                  "      \"username\": \"root\",\n" +
                                                  "      \"email\": \"admin@local.host\",\n" +
                                                  "      \"name\": \"Administrator\",\n" +
                                                  "      \"state\": \"active\",\n" +
                                                  "      \"created_at\": \"2013-11-14T12:19:43Z\"\n" +
                                                  "    },\n" +
                                                  "    \"author\": {\n" +
                                                  "      \"id\": 1,\n" +
                                                  "      \"username\": \"root\",\n" +
                                                  "      \"email\": \"admin@local.host\",\n" +
                                                  "      \"name\": \"Administrator\",\n" +
                                                  "      \"state\": \"active\",\n" +
                                                  "      \"created_at\": \"2013-11-14T12:19:43Z\"\n" +
                                                  "    },\n" +
                                                  "    \"state\": \"opened\",\n" +
                                                  "    \"updated_at\": \"2013-11-14T12:30:39Z\",\n" +
                                                  "    \"created_at\": \"2013-11-14T12:30:39Z\"\n" +
                                                  "  }\n" +
                                                  "] ";

  private static final String SINGLE_TASK_RESPONSE = "{\n" +
                                                     "  \"id\": 2,\n" +
                                                     "  \"iid\": 2,\n" +
                                                     "  \"project_id\": 1,\n" +
                                                     "  \"title\": \"Drink more tea\",\n" +
                                                     "  \"description\": \"It\\u0027s healthy.\",\n" +
                                                     "  \"labels\": [],\n" +
                                                     "  \"assignee\": {\n" +
                                                     "    \"id\": 4,\n" +
                                                     "    \"username\": \"deva\",\n" +
                                                     "    \"email\": \"deva@somemail.com\",\n" +
                                                     "    \"name\": \"John Smith\",\n" +
                                                     "    \"state\": \"active\",\n" +
                                                     "    \"created_at\": \"2013-11-14T12:34:37Z\"\n" +
                                                     "  },\n" +
                                                     "  \"author\": {\n" +
                                                     "    \"id\": 1,\n" +
                                                     "    \"username\": \"root\",\n" +
                                                     "    \"email\": \"admin@local.host\",\n" +
                                                     "    \"name\": \"Administrator\",\n" +
                                                     "    \"state\": \"active\",\n" +
                                                     "    \"created_at\": \"2013-11-14T12:19:43Z\"\n" +
                                                     "  },\n" +
                                                     "  \"state\": \"opened\",\n" +
                                                     "  \"updated_at\": \"2013-11-14T12:38:51Z\",\n" +
                                                     "  \"created_at\": \"2013-11-14T12:33:07Z\"\n" +
                                                     "}";

  private Task getTask1() {
    return new TaskTestUtil.TaskBuilder("1", "Eat more bananas", myRepository)
      .withDescription("They're tasty.")
      .withUpdated("2013-11-14T12:30:39Z")
      .withCreated("2013-11-14T12:30:39Z");
  }

  private Task getTask2() {
    return new TaskTestUtil.TaskBuilder("2", "Drink more tea", myRepository)
      .withDescription("It's healthy.")
      .withUpdated("2013-11-14T12:38:51Z")
      .withCreated("2013-11-14T12:33:07Z");
  }

  @NotNull
  @Override
  protected GenericRepository createRepository(GenericRepositoryType genericType) {
    return (GenericRepository)genericType.new GitlabRepository().createRepository();
  }

  public void testParsingTaskList() throws Exception {
    Task[] tasks = myRepository.getActiveResponseHandler().parseIssues(TASK_LIST_RESPONSE, 50);
    TaskTestUtil.assertTasksEqual(new Task[]{getTask2(), getTask1()}, tasks);
  }

  public void testParsingSingleTask() throws Exception {
    Task task = myRepository.getActiveResponseHandler().parseIssue(SINGLE_TASK_RESPONSE);
    TaskTestUtil.assertTasksEqual(getTask2(), task);
  }
}
