package com.intellij.tasks;

import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {
  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected TaskManagerTestCase() {
    IdeaTestCase.initPlatformPrefix();
  }

  protected TaskManagerImpl myTaskManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    removeAllTasks();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myTaskManager.setRepositories(Collections.<TaskRepository>emptyList());
      removeAllTasks();
    }
    finally {
      myTaskManager = null;
    }
    super.tearDown();
  }

  private void removeAllTasks() {
    List<LocalTask> tasks = myTaskManager.getLocalTasks();
    for (LocalTask task : tasks) {
      myTaskManager.removeTask(task);
    }
  }
}
