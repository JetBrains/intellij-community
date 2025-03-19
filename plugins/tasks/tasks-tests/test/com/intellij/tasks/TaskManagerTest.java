// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskProjectConfiguration;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class TaskManagerTest extends TaskManagerTestCase {
  public void testTaskSwitch() {
    final Ref<Integer> count = Ref.create(0);
    myTaskManager.addTaskListener(new TaskListenerAdapter() {
      @Override
      public void taskActivated(@NotNull LocalTask task) {
        count.set(count.get() + 1);
      }
    }, getTestRootDisposable());
    myTaskManager.activateTask(myTaskManager.createLocalTask("foo"), false);
    assertEquals(1, count.get().intValue());

    myTaskManager.activateTask(myTaskManager.createLocalTask("bar"), false);
    assertEquals(2, count.get().intValue());
  }

  public void testNotifications() {
    final Ref<Notification> notificationRef = new Ref<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        notificationRef.set(notification);
      }
    });

    TestRepository repository = new TestRepository() {
      @Override
      public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
        throw new Exception();
      }
    };
    myTaskManager.setRepositories(Collections.singletonList(repository));

    myTaskManager.updateIssues(null);

    assertNull(notificationRef.get());

    TaskSearchSupport.getRepositoriesTasks(getProject(), "", 0, 100, true, false, new EmptyProgressIndicator());

    assertNotNull(notificationRef.get());
  }

  public void testSharedServers() {
    TaskRepository repository = new YouTrackRepository(new YouTrackRepositoryType());
    repository.setShared(true);
    myTaskManager.setRepositories(Collections.singletonList(repository));

    TaskProjectConfiguration configuration = getProject().getService(TaskProjectConfiguration.class);
    TaskProjectConfiguration state = configuration.getState();
    assertNotNull(state);
    assertEquals(1, state.servers.size());
    Element element = XmlSerializer.serialize(state);

    configuration.servers.clear();
    myTaskManager.setRepositories(Collections.emptyList());

    configuration.loadState(XmlSerializer.deserialize(element, TaskProjectConfiguration.class));
    assertEquals(1, state.servers.size());

    myTaskManager.callProjectOpened();

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(1, repositories.length);
    assertTrue(repositories[0].isShared());
  }

  public void testRemoveShared() {
    TaskRepository repository = new YouTrackRepository(new YouTrackRepositoryType());
    repository.setShared(true);
    myTaskManager.setRepositories(Collections.singletonList(repository));

    myTaskManager.setRepositories(Collections.emptyList());

    TaskProjectConfiguration configuration = getProject().getService(TaskProjectConfiguration.class);
    assertEquals(0, configuration.getState().servers.size());
  }

  public void testIssuesCacheSurvival() {
    final Ref<Boolean> stopper = new Ref<>(Boolean.FALSE);
    TestRepository repository = new TestRepository(new LocalTaskImpl("foo", "bar")) {
      @Override
      public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
        if (stopper.get()) throw new Exception();
        return super.getIssues(query, max, since);
      }
    };
    myTaskManager.setRepositories(Collections.singletonList(repository));

    List<Task> issues = myTaskManager.getIssues("");
    assertEquals(1, issues.size());

    stopper.set(Boolean.TRUE);
    TaskSearchSupport.getRepositoriesTasks(getProject(), "", 0, 100, true, false, new EmptyProgressIndicator());
    assertEquals(1, issues.size());
  }

  public void testTaskHistoryLength() {
    TestRepository repository = new TestRepository();
    int historyLength = myTaskManager.getState().taskHistoryLength;
    for (int i = 0; i < historyLength + 100; i++) {
      myTaskManager.addTask(new TaskTestUtil.TaskBuilder(Integer.toString(i), "", repository).withClosed(true));
    }
    assertEquals(historyLength, myTaskManager.getLocalTasks().size());
    assertEquals(Integer.toString(historyLength + 100 - 1), myTaskManager.getLocalTasks().get(historyLength - 1).getId());
  }

  public void testBranchNameSuggestion() {
    TaskTestUtil.TaskBuilder task = new TaskTestUtil.TaskBuilder("IDEA-666", "Bad news", null);
    TaskManagerImpl taskManager = myTaskManager;
    assertEquals("IDEA-666", taskManager.suggestBranchName(task));
    String format = taskManager.getState().branchNameFormat;
    try {
      taskManager.getState().branchNameFormat = "feature/{id}";
      assertEquals("feature/IDEA-666", taskManager.suggestBranchName(task));
      taskManager.getState().branchNameFormat = "{id}_{summary}";
      assertEquals("IDEA-666_Bad-news", taskManager.suggestBranchName(task));
    }
    finally {
      taskManager.getState().branchNameFormat = format;
    }
  }

  public void testPreserveTaskUrl() {
    String url = "http://server/foo";
    myTaskManager.addTask(new TaskTestUtil.TaskBuilder("foo", "summary", null).withIssueUrl(url));
    TaskManagerImpl.Config config = getConfig();
    LocalTaskImpl task = config.tasks.get(1);
    assertEquals(url, task.getIssueUrl());
  }

  private TaskManagerImpl.Config getConfig() {
    Element element = XmlSerializer.serialize(myTaskManager.getState());
    return XmlSerializer.deserialize(element, TaskManagerImpl.Config.class);
  }

  public void testRestoreRepository() {
    TestRepository repository = new TestRepository();
    repository.setUrl("http://server");
    myTaskManager.setRepositories(Collections.singletonList(repository));

    TaskTestUtil.TaskBuilder issue = new TaskTestUtil.TaskBuilder("foo", "summary", repository).withIssueUrl(repository.getUrl() + "/foo");
    myTaskManager.activateTask(issue, false);
    TaskManagerImpl.Config config = getConfig();
    myTaskManager.loadState(config);

    assertEquals(repository, myTaskManager.getActiveTask().getRepository());
  }

  public void testCopyPresentableName() {
    LocalTaskImpl task = new LocalTaskImpl("007", "");
    LocalTaskImpl copy = new LocalTaskImpl(task);
    copy.setSummary("foo");
    assertEquals("foo", copy.getPresentableName());
  }

  public void testUpdateToVelocity() {
    TaskManagerImpl.Config config = getConfig();
    config.branchNameFormat = "{id}";
    config.changelistNameFormat = "{id} {summary}";
    myTaskManager.loadState(config);
    assertEquals("${id}", myTaskManager.getState().branchNameFormat);
    assertEquals("${id} ${summary}", myTaskManager.getState().changelistNameFormat);
  }
}
