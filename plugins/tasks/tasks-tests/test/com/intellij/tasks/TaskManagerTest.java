package com.intellij.tasks;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.Notifications;
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

    TaskListener listener = new TaskListener() {
      @Override
      public void taskActivated(LocalTask task) {

      }
    };
    myManager.addTaskListener(listener);
    myManager.removeTaskListener(listener);
  }

  public void testTaskSwitch() throws Exception {

    final Ref<Integer> count = Ref.create(0);
    TaskListener listener = new TaskListener() {
      @Override
      public void taskActivated(LocalTask task) {
        count.set(count.get() + 1);
      }
    };
    myManager.addTaskListener(listener);
    LocalTask localTask = myManager.createLocalTask("foo");
    myManager.activateTask(localTask, false, false);
    assertEquals(1, count.get().intValue());

    LocalTask other = myManager.createLocalTask("bar");
    myManager.activateTask(other, false, false);
    assertEquals(2, count.get().intValue());

    myManager.removeTaskListener(listener);
  }

  public void testNotifications() throws Exception {

    final Ref<Notification> notificationRef = new Ref<Notification>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        notificationRef.set(notification);
      }

      @Override
      public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {

      }

      @Override
      public void register(@NotNull String groupDisplayName,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean shouldLog) {

      }
    });

    TestRepository repository = new TestRepository() {
      @Override
      public List<Task> getIssues(@Nullable String query, int max, long since) throws Exception {
        throw new Exception();
      }
    };
    myManager.setRepositories(Collections.singletonList(repository));

    myManager.updateIssues(null);

    assertNull(notificationRef.get());

    myManager.getIssues("");

    assertNotNull(notificationRef.get());
  }

  public void testSharedServers() throws Exception {
    TaskRepository repository = new YouTrackRepository(new YouTrackRepositoryType());
    repository.setShared(true);
    myManager.setRepositories(Collections.singletonList(repository));

    TaskProjectConfiguration configuration = ServiceManager.getService(getProject(), TaskProjectConfiguration.class);
    TaskProjectConfiguration state = configuration.getState();
    assertNotNull(state);
    assertEquals(1, state.servers.size());
    Element element = XmlSerializer.serialize(state);

    configuration.servers.clear();
    myManager.setRepositories(Collections.<TaskRepository>emptyList());

    configuration.loadState(XmlSerializer.deserialize(element, TaskProjectConfiguration.class));
    assertEquals(1, state.servers.size());

    myManager.projectOpened();

    TaskRepository[] repositories = myManager.getAllRepositories();
    assertEquals(1, repositories.length);
  }

  public void testIssuesCacheSurvival() throws Exception {
    final Ref<Boolean> stopper = new Ref<Boolean>(Boolean.FALSE);
    TestRepository repository = new TestRepository(new LocalTaskImpl("foo", "bar")) {
      @Override
      public List<Task> getIssues(@Nullable String query, int max, long since) throws Exception {
        if (stopper.get()) throw new Exception();
        return super.getIssues(query, max, since);
      }
    };
    myManager.setRepositories(Collections.singletonList(repository));

    List<Task> issues = myManager.getIssues("");
    assertEquals(1, issues.size());

    stopper.set(Boolean.TRUE);
    issues = myManager.getIssues("");
    assertEquals(1, issues.size());
  }
}
