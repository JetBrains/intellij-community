package com.intellij.tasks.jira;

import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.rest.api2.JiraRestApi2;
import com.intellij.tasks.jira.rest.api20alpha1.JiraRestApi20Alpha1;
import com.intellij.tasks.jira.soap.JiraSoapApi;
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

  @NotNull
  public abstract List<Task> findTasks(@NotNull String jql, int max) throws Exception;

  @Nullable
  public abstract Task findTask(@NotNull String key) throws Exception;

  public abstract void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception;

  public abstract void updateTimeSpend(@NotNull LocalTask task, @NotNull String timeSpent, String comment) throws Exception;

  @NotNull
  public final String getVersionName() {
    return getType().getVersionName();
  }

  @Override
  public final String toString() {
    return "JiraRemoteApi(" + getType().getVersionName() + ")";
  }

  @NotNull
  public abstract ApiType getType();

  public enum ApiType {
    SOAP("SOAP") {
      @NotNull
      @Override
      public JiraSoapApi createApi(@NotNull JiraRepository repository) {
        return new JiraSoapApi(repository);
      }
    },
    REST_2_0("REST 2.0") {
      @NotNull
      @Override
      public JiraRestApi2 createApi(@NotNull JiraRepository repository) {
        return new JiraRestApi2(repository);
      }
    },
    REST_2_0_ALPHA("REST 2.0.alpha1") {
      @NotNull
      @Override
      public JiraRestApi20Alpha1 createApi(@NotNull JiraRepository repository) {
        return new JiraRestApi20Alpha1(repository);
      }
    };

    ApiType(String versionName) {
      myVersionName = versionName;
    }
    private String myVersionName;
    @NotNull
    public abstract JiraRemoteApi createApi(@NotNull JiraRepository repository);

    @NotNull
    public String getVersionName() {
      return myVersionName;
    }

  }
}
