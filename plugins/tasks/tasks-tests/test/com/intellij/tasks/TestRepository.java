package com.intellij.tasks;

import com.intellij.openapi.util.Condition;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

/**
* @author Dmitry Avdeev
*/
public class TestRepository extends BaseRepository {
  private Task[] myTasks;

  public TestRepository(Task... tasks) {
    myTasks = tasks;
  }

  public void setTasks(Task... tasks) {
    myTasks = tasks;
  }

  @Override
  public BaseRepository clone() {
    return this;
  }

  @Override
  public void testConnection() throws Exception {
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    return myTasks;
  }

  @Nullable
  @Override
  public Task findTask(final String id) throws Exception {
    return ContainerUtil.find(myTasks, new Condition<Task>() {
      @Override
      public boolean value(Task task) {
        return id.equals(task.getId());
      }
    });
  }

  @Override
  public boolean isConfigured() {
    return true;
  }
}
