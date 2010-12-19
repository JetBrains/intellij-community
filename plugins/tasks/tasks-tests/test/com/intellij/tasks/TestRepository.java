package com.intellij.tasks;

import com.intellij.tasks.impl.BaseRepository;
import org.jetbrains.annotations.Nullable;

/**
* @author Dmitry Avdeev
*/
class TestRepository extends BaseRepository {
  private final Task[] myTasks;

  public TestRepository(Task... tasks) {
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

  @Override
  public Task findTask(String id) throws Exception {
    return null;
  }

  @Override
  public String extractId(String taskName) {
    return null;
  }

  @Override
  public boolean isConfigured() {
    return true;
  }
}
