// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.integration;

import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil;
import com.intellij.tasks.youtrack.YouTrackIntellisense;
import com.intellij.tasks.youtrack.YouTrackIntellisense.CompletionItem;
import com.intellij.tasks.youtrack.YouTrackIntellisense.HighlightRange;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class YouTrackIntegrationTest extends TaskManagerTestCase {
  private static final String APPLICATION_PASSWORD = System.getProperty("tasks.tests.youtrack.application.password");
  private static final String SERVER_URL = "https://yt-ij-integration-tests.myjetbrains.com/youtrack";

  private static class Issues {
    static final String ASSIGNED_OPEN_BUG = "TEST-1";
    static final String ASSIGNED_CLOSED_BUG = "TEST-2";
    static final String UNASSIGNED_OPEN_BUG = "TEST-3";
    static final String ASSIGNED_OPEN_TASK = "TEST-5";
    static final String FOR_STATE_UPDATING = "TEST-4";
    static final String FOR_TIME_TRACKING = "TEST-6";
  }

  private YouTrackRepository myRepository;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = new YouTrackRepository(new YouTrackRepositoryType());
    myRepository.setUrl(SERVER_URL);
    myRepository.setUsername("root");
    myRepository.setPassword(APPLICATION_PASSWORD);

    Logger.getInstance(TaskResponseUtil.class).setLevel(LogLevel.DEBUG);
    Logger.getInstance("org.apache.http").setLevel(LogLevel.DEBUG);
  }

  public void testDefaultQueryResults() throws Exception {
    Task[] results = myRepository.getIssues("", 0, 10, false);
    Task assignedOpenBug = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_OPEN_BUG));
    assertNotNull(assignedOpenBug);

    Task assignedOpenTask = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_OPEN_TASK));
    assertNotNull(assignedOpenTask);

    Task assignedClosed = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_CLOSED_BUG));
    assertNull(assignedClosed);

    Task unassignedOpen = ContainerUtil.find(results, task -> task.getId().equals(Issues.UNASSIGNED_OPEN_BUG));
    assertNull(unassignedOpen);
  }

  public void testCustomQueryResults() throws Exception {
    Task[] results = myRepository.getIssues("#Task", 0, 10, false);
    Task assignedOpenBug = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_OPEN_BUG));
    assertNull(assignedOpenBug);

    Task assignedOpenTask = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_OPEN_TASK));
    assertNotNull(assignedOpenTask);

    Task assignedClosed = ContainerUtil.find(results, task -> task.getId().equals(Issues.ASSIGNED_CLOSED_BUG));
    assertNull(assignedClosed);

    Task unassignedOpen = ContainerUtil.find(results, task -> task.getId().equals(Issues.UNASSIGNED_OPEN_BUG));
    assertNull(unassignedOpen);
  }

  public void testInvalidQueryDoNotCauseRequestFailedExceptions() throws Exception {
    try {
      Task[] results = myRepository.getIssues("#Tas", 0, 10, false);
      assertEmpty(results);
    }
    catch (RequestFailedException e) {
      fail("Invalid query errors should be suppressed, but '" + e.getMessage() + "' was thrown");
    }
  }

  public void testDirectIssueIdQuery() throws Exception {
    Task[] results = myRepository.getIssues("#" + Issues.ASSIGNED_OPEN_BUG, 0, 10, false);
    Task onlyTask = assertOneElement(results);
    assertEquals(Issues.ASSIGNED_OPEN_BUG, onlyTask.getId());
  }

  public void testFetchingSingleOpenIssue() throws Exception {
    Task task = myRepository.findTask(Issues.ASSIGNED_OPEN_BUG);
    assertEquals(Issues.ASSIGNED_OPEN_BUG, task.getId());
    assertEquals(myRepository, task.getRepository());

    assertEquals(TaskType.BUG, task.getType());
    assertEquals(TaskState.OPEN, task.getState());

    assertEquals("Assigned to root, open", task.getSummary());
    assertEquals("Description: Assigned to root, open.", task.getDescription());
    assertEquals(SERVER_URL + "/issue/" + Issues.ASSIGNED_OPEN_BUG, task.getIssueUrl());

    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("Europe/Moscow"), Locale.ENGLISH);
    calendar.setTime(task.getCreated());
    assertEquals(2020, calendar.get(Calendar.YEAR));
    assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH));
    assertEquals(28, calendar.get(Calendar.DATE));
    assertEquals(13, calendar.get(Calendar.HOUR_OF_DAY));

    assertTrue(task.getCreated().compareTo(task.getUpdated()) < 0);

    assertTrue(task.isIssue());
    assertFalse(task.isClosed());
  }

  public void testFetchingSingleClosedIssue() throws Exception {
    Task task = myRepository.findTask(Issues.ASSIGNED_CLOSED_BUG);
    assertEquals(Issues.ASSIGNED_CLOSED_BUG, task.getId());
    assertEquals(myRepository, task.getRepository());

    assertEquals(TaskType.BUG, task.getType());
    assertEquals(TaskState.RESOLVED, task.getState());

    assertEquals("Assigned to root, closed", task.getSummary());
    assertEquals("Description: Assigned to root, closed.", task.getDescription());
    assertEquals(SERVER_URL + "/issue/" + Issues.ASSIGNED_CLOSED_BUG, task.getIssueUrl());

    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("Europe/Moscow"), Locale.ENGLISH);
    calendar.setTime(task.getCreated());
    assertEquals(2020, calendar.get(Calendar.YEAR));
    assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH));
    assertEquals(28, calendar.get(Calendar.DATE));
    assertEquals(13, calendar.get(Calendar.HOUR_OF_DAY));

    assertTrue(task.getCreated().compareTo(task.getUpdated()) < 0);

    assertEquals(TaskType.BUG, task.getType());
    assertTrue(task.isIssue());
    assertTrue(task.isClosed());
  }

  public void testUpdatingIssueState() throws Exception {
    Task task = myRepository.findTask(Issues.FOR_STATE_UPDATING);
    try {
      assertEquals(TaskState.OPEN, task.getState());

      Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
      CustomTaskState fixedState = ContainerUtil.find(states, s -> s.getPresentableName().equals("Fixed"));
      assertNotNull(fixedState);

      myRepository.setTaskState(task, fixedState);

      task = myRepository.findTask(Issues.FOR_STATE_UPDATING);
      assertEquals(TaskState.RESOLVED, task.getState());
    }
    finally {
      myRepository.setTaskState(task, new CustomTaskState("Open", "Open"));
    }
  }

  public void testUpdatingTimeSpent() throws Exception {
    Task task = myRepository.findTask(Issues.FOR_TIME_TRACKING);
    assertNotNull(task);

    LocalTaskImpl localTask = new LocalTaskImpl(task);
    myRepository.updateTimeSpent(localTask, "0h 10m", "From unit tests");
  }

  public void testWrappingErrors() throws Exception {
    Task task = myRepository.findTask(Issues.FOR_STATE_UPDATING);
    try {
      assertEquals(TaskState.OPEN, task.getState());

      Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
      CustomTaskState fixedState = ContainerUtil.find(states, s -> s.getPresentableName().equals("Duplicate"));
      assertNotNull(fixedState);

      myRepository.setTaskState(task, fixedState);
      fail("Setting 'Duplicate' state without an issue ID should fail");
    }
    catch (RequestFailedException e) {
      assertEquals(e.getMessage(), "duplicates is required, details: Add link to duplicate issue.");
    }
  }

  public void testPagination() throws Exception {
    myRepository.setDefaultSearch("project: PGN sort by: created asc");

    Task[] firstPage = myRepository.getIssues(null, 0, 5, false);
    assertSize(5, firstPage);
    assertContainsOrdered(ContainerUtil.map(firstPage, Task::getId), "PGN-1", "PGN-2", "PGN-3", "PGN-4", "PGN-5");

    Task[] secondPage = myRepository.getIssues(null, 5, 5, false);
    assertSize(5, secondPage);
    assertContainsOrdered(ContainerUtil.map(secondPage, Task::getId), "PGN-6", "PGN-7", "PGN-8", "PGN-9", "PGN-10");

    Task[] thirdPage = myRepository.getIssues(null, 10, 5, false);
    assertSize(2, thirdPage);
    assertContainsOrdered(ContainerUtil.map(thirdPage, Task::getId), "PGN-11", "PGN-12");
  }

  public void testSearchQueryHighlighting() throws Exception {
    YouTrackIntellisense intellisense = new YouTrackIntellisense(myRepository);
    List<HighlightRange> ranges = intellisense.fetchHighlighting("for: me #Unresolved", 5);
    assertSize(3, ranges);

    assertEquals(TextRange.create(0, 3), ranges.get(0).getTextRange());
    assertEquals("field-name", ranges.get(0).getStyleClass());
    assertEquals(DefaultLanguageHighlighterColors.KEYWORD.getDefaultAttributes(), ranges.get(0).getTextAttributes());

    assertEquals(TextRange.create(5, 7), ranges.get(1).getTextRange());
    assertEquals("field-value", ranges.get(1).getStyleClass());
    assertEquals(DefaultLanguageHighlighterColors.CONSTANT.getDefaultAttributes(), ranges.get(1).getTextAttributes());

    assertEquals(TextRange.create(9, 19), ranges.get(2).getTextRange());
    assertEquals("field-value", ranges.get(2).getStyleClass());
    assertEquals(DefaultLanguageHighlighterColors.CONSTANT.getDefaultAttributes(), ranges.get(2).getTextAttributes());
  }

  public void testSearchQueryCompletion() throws Exception {
    YouTrackIntellisense intellisense = new YouTrackIntellisense(myRepository);
    List<CompletionItem> completionItems = intellisense.fetchCompletion("type: ", 6);
    List<String> variants = ContainerUtil.map(completionItems, item -> item.getOption());
    assertContainsElements(variants,"Bug", "Task", "Feature");
    CompletionItem bugState = ContainerUtil.find(completionItems, item -> item.getOption().equals("Bug"));
    assertTrue(bugState.getDescription().contains("Type in"));
  }
}
