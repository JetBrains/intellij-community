package com.intellij.tasks;

import com.intellij.openapi.util.Ref;

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

}
