/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Ref;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskProjectConfiguration;
import com.intellij.tasks.youtrack.YouTrackRepository;
import com.intellij.tasks.youtrack.YouTrackRepositoryType;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class TaskManagerTest extends TaskManagerTestCase {
  public void testTaskSwitch() throws Exception {

    final Ref<Integer> count = Ref.create(0);
    TaskListener listener = new TaskListenerAdapter() {
      @Override
      public void taskActivated(LocalTask task) {
        count.set(count.get() + 1);
      }
    };
    myTaskManager.addTaskListener(listener, getTestRootDisposable());
    LocalTask localTask = myTaskManager.createLocalTask("foo");
    myTaskManager.activateTask(localTask, false);
    assertEquals(1, count.get().intValue());

    LocalTask other = myTaskManager.createLocalTask("bar");
    myTaskManager.activateTask(other, false);
    assertEquals(2, count.get().intValue());
  }

  public void testNotifications() throws Exception {

    final Ref<Notification> notificationRef = new Ref<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new NotificationsAdapter() {
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
    ((TaskManagerImpl)myTaskManager).setRepositories(Collections.singletonList(repository));

    myTaskManager.updateIssues(null);

    assertNull(notificationRef.get());

    myTaskManager.getIssues("");

    assertNotNull(notificationRef.get());
  }

  public void testSharedServers() throws Exception {
    TaskRepository repository = new YouTrackRepository(new YouTrackRepositoryType());
    repository.setShared(true);
    ((TaskManagerImpl)myTaskManager).setRepositories(Collections.singletonList(repository));

    TaskProjectConfiguration configuration = ServiceManager.getService(getProject(), TaskProjectConfiguration.class);
    TaskProjectConfiguration state = configuration.getState();
    assertNotNull(state);
    assertEquals(1, state.servers.size());
    Element element = XmlSerializer.serialize(state);

    configuration.servers.clear();
    ((TaskManagerImpl)myTaskManager).setRepositories(Collections.<TaskRepository>emptyList());

    configuration.loadState(XmlSerializer.deserialize(element, TaskProjectConfiguration.class));
    assertEquals(1, state.servers.size());

    ((TaskManagerImpl)myTaskManager).projectOpened();

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(1, repositories.length);
    assertTrue(repositories[0].isShared());
  }

  public void testIssuesCacheSurvival() throws Exception {
    final Ref<Boolean> stopper = new Ref<>(Boolean.FALSE);
    TestRepository repository = new TestRepository(new LocalTaskImpl("foo", "bar")) {
      @Override
      public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
        if (stopper.get()) throw new Exception();
        return super.getIssues(query, max, since);
      }
    };
    ((TaskManagerImpl)myTaskManager).setRepositories(Collections.singletonList(repository));

    List<Task> issues = myTaskManager.getIssues("");
    assertEquals(1, issues.size());

    stopper.set(Boolean.TRUE);
    issues = myTaskManager.getIssues("");
    assertEquals(1, issues.size());
  }

  public void testTaskHistoryLength() throws Exception {
    TestRepository repository = new TestRepository();
    int historyLength = ((TaskManagerImpl)myTaskManager).getState().taskHistoryLength;
    for (int i = 0; i < historyLength + 100; i++) {
      myTaskManager.addTask(new TaskTestUtil.TaskBuilder(Integer.toString(i), "", repository));
    }
    assertEquals(historyLength, myTaskManager.getLocalTasks().size());
    assertEquals(Integer.toString(historyLength + 100 - 1), myTaskManager.getLocalTasks().get(historyLength - 1).getId());
  }

  public void testBranchNameSuggestion() throws Exception {
    TaskTestUtil.TaskBuilder task = new TaskTestUtil.TaskBuilder("IDEA-666", "Bad news", null);
    TaskManagerImpl taskManager = (TaskManagerImpl)myTaskManager;
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
}
