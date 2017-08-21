package com.intellij.tasks;

import com.intellij.tasks.impl.BaseRepository;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
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

  @NotNull
  @Override
  public BaseRepository clone() {
    return this;
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    return myTasks;
  }

  @Nullable
  @Override
  public Task findTask(@NotNull final String id) {
    return ContainerUtil.find(myTasks, task -> id.equals(task.getId()));
  }

  @Override
  public boolean isConfigured() {
    return true;
  }
}
