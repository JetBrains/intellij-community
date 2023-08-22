// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.live;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.pivotal.PivotalTrackerRepository;
import com.intellij.tasks.pivotal.PivotalTrackerRepositoryType;
import com.intellij.tasks.pivotal.PivotalTrackerTask;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;


public class PivotalIntegrationTest extends LiveIntegrationTestCase<PivotalTrackerRepository> {
  private static final String INTEGRATION_TESTS_PROJECT_ID = "2243263";
  private static final String STARTED_STORY_ID = "163682205";
  private static final String STORY_FOR_STATE_UPDATES_ID = "163693218";

  private static final CustomTaskState STARTED_STATE = new CustomTaskState("started", "started");
  private static final CustomTaskState FINISHED_STATE = new CustomTaskState("finished", "finished");

  @Override
  protected PivotalTrackerRepository createRepository() throws Exception {
    final PivotalTrackerRepository repository = new PivotalTrackerRepository(new PivotalTrackerRepositoryType());
    repository.setPassword(System.getProperty("tasks.tests.pivotal.tracker.token"));
    repository.setProjectId(INTEGRATION_TESTS_PROJECT_ID);
    return repository;
  }

  private static void checkStartedStoryTaskProperties(@NotNull Task task) {
    assertEquals("Started story", task.getSummary());
    assertEquals("Description.", task.getDescription());
    assertFalse(task.isClosed());
    assertNull(task.getState());
    // Not yet supported
    assertEmpty(task.getComments());
    assertEquals("https://www.pivotaltracker.com/story/show/163682205", task.getIssueUrl());
    assertEquals("#" + STARTED_STORY_ID, task.getPresentableId());
    assertEquals(STARTED_STORY_ID, task.getNumber());
    assertEquals(INTEGRATION_TESTS_PROJECT_ID + "-" + STARTED_STORY_ID, task.getId());
    // Checking getUpdated() is to brittle
    assertEquals(TaskUtil.parseDate("2019-02-03T13:34:15Z"), task.getCreated());
  }

  public void testFetchingAllStories() throws Exception {
    final Task[] tasks = myRepository.getIssues("", 0, 10, false);
    final Task startedStoryTask = ContainerUtil.find(tasks, task -> "Started story".equals(task.getSummary()));
    assertNotNull(startedStoryTask);
    checkStartedStoryTaskProperties(startedStoryTask);
  }

  public void testFetchingWithFilteringStoriesByQuery() throws Exception {
    final Task[] tasks = myRepository.getIssues("matching query", 0, 10, false);
    assertOneElement(tasks);
    assertEquals("Story matching query", tasks[0].getSummary());
  }

  public void testFetchingClosedStories() throws Exception {
    final Task[] openTasks = myRepository.getIssues("", 0, 10, false);
    assertDoesntContain(ContainerUtil.map(openTasks, Task::getSummary), "Finished story");
    assertTrue(ContainerUtil.and(openTasks, task -> !task.isClosed()));

    final Task[] allTasks = myRepository.getIssues("", 0, 10, true);
    assertContainsElements(ContainerUtil.map(allTasks, Task::getSummary), "Finished story");
    final Task finished = ContainerUtil.find(allTasks, task -> task.getSummary().equals("Finished story"));
    assertTrue(finished.isClosed());
  }

  public void testFetchingSingleStory() throws Exception {
    final Task task = myRepository.findTask(INTEGRATION_TESTS_PROJECT_ID + "-" + STARTED_STORY_ID);
    assertNotNull(task);
    assertEquals(STARTED_STORY_ID, task.getNumber());
    assertEquals("#" + STARTED_STORY_ID, task.getPresentableId());
    assertEquals(INTEGRATION_TESTS_PROJECT_ID + "-" + STARTED_STORY_ID, task.getId());
    assertNotNull(task);
    checkStartedStoryTaskProperties(task);
  }

  public void testStoryStateUpdating() throws Exception {
    final String taskId = INTEGRATION_TESTS_PROJECT_ID + "-" + STORY_FOR_STATE_UPDATES_ID;

    PivotalTrackerTask task = ((PivotalTrackerTask)myRepository.findTask(taskId));
    assertEquals("started", task.getStory().getCurrentState());

    myRepository.setTaskState(task, FINISHED_STATE);

    task = ((PivotalTrackerTask)myRepository.findTask(taskId));
    assertEquals("finished", task.getStory().getCurrentState());

    myRepository.setTaskState(task, STARTED_STATE);

    task = ((PivotalTrackerTask)myRepository.findTask(taskId));
    assertEquals("started", task.getStory().getCurrentState());
  }

  // IDEA-206556
  public void testApiTokenNotStoredInSettings() {
    final String token = "secret";
    myRepository.setPassword(token);
    final Element serialized = XmlSerializer.serialize(myRepository);
    final String configContent = JDOMUtil.write(serialized);
    assertFalse(configContent.contains(token));
  }
}
