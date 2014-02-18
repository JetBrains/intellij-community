package com.intellij.tasks.jira;

import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Because of the number of available remote interfaces in JIRA, {@link JiraRepository} delegates
 * almost all its functionality to classes extending this class.
 *
 * @author Mikhail Golubev
 */
public abstract class JiraRemoteApi {
  protected final JiraRepository myRepository;
  protected JiraRemoteApi(@NotNull JiraRepository repository) {
    myRepository = repository;
  }

  /**
   * Used to clone original repository in {@link TaskRepositoryConfigurable}
   * @param repository new repository for which copy should be created
   * @return new instance of this {@link JiraRemoteApi}
   */
  public abstract JiraRemoteApi cloneFor(@NotNull JiraRepository repository);

  @NotNull
  public abstract List<Task> findTasks(String jql, int max) throws Exception;

  @Nullable
  public abstract Task findTask(String key) throws Exception;

  public abstract void setTaskState(Task task, TaskState state) throws Exception;

  public abstract void updateTimeSpend(LocalTask task, String timeSpent, String comment) throws Exception;

  @NotNull
  public abstract String getVersionName();

  @NotNull
  public abstract ApiType getType();

  public enum ApiType {
    SOAP,
    REST
  }
}
