package com.intellij.tasks;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

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
      public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
        throw new Exception();
      }
    };
    myManager.setRepositories(Collections.singletonList(repository));

    myManager.updateIssues(null);

    assertNull(notificationRef.get());

    myManager.getIssues("");

    assertNotNull(notificationRef.get());
  }
}
