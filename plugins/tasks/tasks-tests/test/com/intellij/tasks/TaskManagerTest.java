package com.intellij.tasks;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Ref;
import com.intellij.tasks.impl.LocalTaskImpl;
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

  public void testAddRemoveListener() throws Exception {

    TaskListener listener = new TaskListenerAdapter() {
      @Override
      public void taskActivated(LocalTask task) {

      }
    };
    myTaskManager.addTaskListener(listener);
    myTaskManager.removeTaskListener(listener);
  }

  public void testTaskSwitch() throws Exception {

    final Ref<Integer> count = Ref.create(0);
    TaskListener listener = new TaskListenerAdapter() {
      @Override
      public void taskActivated(LocalTask task) {
        count.set(count.get() + 1);
      }
    };
    myTaskManager.addTaskListener(listener);
    LocalTask localTask = myTaskManager.createLocalTask("foo");
    myTaskManager.activateTask(localTask, false);
    assertEquals(1, count.get().intValue());

    LocalTask other = myTaskManager.createLocalTask("bar");
    myTaskManager.activateTask(other, false);
    assertEquals(2, count.get().intValue());

    myTaskManager.removeTaskListener(listener);
  }

  public void testNotifications() throws Exception {

    final Ref<Notification> notificationRef = new Ref<Notification>();
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
    myTaskManager.setRepositories(Collections.singletonList(repository));

    myTaskManager.updateIssues(null);

    assertNull(notificationRef.get());

    myTaskManager.getIssues("");

    assertNotNull(notificationRef.get());
  }

  public void testSharedServers() throws Exception {
    TaskRepository repository = new YouTrackRepository(new YouTrackRepositoryType());
    repository.setShared(true);
    myTaskManager.setRepositories(Collections.singletonList(repository));

    TaskProjectConfiguration configuration = ServiceManager.getService(getProject(), TaskProjectConfiguration.class);
    TaskProjectConfiguration state = configuration.getState();
    assertNotNull(state);
    assertEquals(1, state.servers.size());
    Element element = XmlSerializer.serialize(state);

    configuration.servers.clear();
    myTaskManager.setRepositories(Collections.<TaskRepository>emptyList());

    configuration.loadState(XmlSerializer.deserialize(element, TaskProjectConfiguration.class));
    assertEquals(1, state.servers.size());

    myTaskManager.projectOpened();

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(1, repositories.length);
  }

  public void testIssuesCacheSurvival() throws Exception {
    final Ref<Boolean> stopper = new Ref<Boolean>(Boolean.FALSE);
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
    issues = myTaskManager.getIssues("");
    assertEquals(1, issues.size());
  }
}
