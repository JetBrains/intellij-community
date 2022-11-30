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
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import com.intellij.tasks.generic.GenericTask;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.tasks.TaskTestUtil.TaskBuilder;
import static com.intellij.tasks.TaskTestUtil.assertTasksEqual;

/**
 * @author Mikhail Golubev
 */
public class AsanaIntegrationTest extends GenericSubtypeTestCase {
  private static final String TASK_LIST_RESPONSE = """
    {
      "data": [
        {
          "id": 5479650606120,
          "name": "Task #1"
        },
        {
          "id": 5202014833559,
          "name": "Task #2"
        }
      ]
    }""";

  private static final String SINGLE_TASK_RESPONSE = """
    {
      "data": {
        "id": 7119324862208,
        "created_at": "2013-08-02T12:13:20.372Z",
        "modified_at": "2013-08-21T16:36:36.290Z",
        "name": "Task #2",
        "notes": "This is task #2 description",
        "assignee": {
          "id": 5202009297038,
          "name": "someuser"
        },
        "completed": true,
        "assignee_status": "inbox",
        "completed_at": "2013-08-21T16:36:35.574Z",
        "due_on": null,
        "projects": [
          {
            "id": 7119324862204,
            "name": "someproject"
          }
        ],
        "tags": [],
        "workspace": {
          "id": 5202014679955,
          "name": "someworkspace"
        },
        "parent": null,
        "followers": [
          {
            "id": 5202009297038,
            "name": "someuser"
          }
        ]
      }
    }""";

  @NotNull
  @Override
  protected GenericRepository createRepository(GenericRepositoryType genericType) {
    return (GenericRepository)genericType.new AsanaRepository().createRepository();
  }

  public void testParsingTaskList() throws Exception {
    // Don't forget to extract summary here, even though it doesn't happen actually when myRepository#getIssues is called
    myRepository.setDownloadTasksInSeparateRequests(false);
    Task[] tasks = myRepository.getActiveResponseHandler().parseIssues(TASK_LIST_RESPONSE, 50);
    List<Task> expected = List.of(new GenericTask("5479650606120", "Task #1", myRepository),
                                  new GenericTask("5202014833559", "Task #2", myRepository));
    assertTasksEqual(expected, Arrays.asList(tasks));
  }

  public void testParsingSingleTask() throws Exception {
    Task task = myRepository.getActiveResponseHandler().parseIssue(SINGLE_TASK_RESPONSE);
    assertNotNull(task);
    assertTasksEqual(
      new TaskBuilder("7119324862208", "Task #2", myRepository)
        .withDescription("This is task #2 description")
        .withClosed(true)
        .withCreated("2013-08-02T12:13:20.372Z")
        .withUpdated("2013-08-21T16:36:36.290Z"),
      task);
  }
}
