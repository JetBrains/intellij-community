package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class GenericSubtypeTestCase extends TaskManagerTestCase {
  protected GenericRepository myRepository;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final GenericRepositoryType genericType = new GenericRepositoryType();
    myRepository = createRepository(genericType);
  }

  @NotNull
  protected abstract GenericRepository createRepository(GenericRepositoryType genericType);

  protected static void  assertTasksEqual(@NotNull Task t1, @NotNull Task t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }

  protected static void  assertTasksEqual(@NotNull List<? extends Task> t1, @NotNull List<? extends Task> t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }

  protected static void  assertTasksEqual(@NotNull Task[] t1, @NotNull Task[] t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }


}
