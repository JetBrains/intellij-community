// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira;

import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.jira.rest.api2.JiraRestApi2;
import com.intellij.tasks.jira.rest.api20alpha1.JiraRestApi20Alpha1;
import com.intellij.tasks.jira.soap.JiraLegacyApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

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

  public abstract @NotNull List<Task> findTasks(@NotNull String jql, int max) throws Exception;

  public abstract @Nullable Task findTask(@NotNull String key) throws Exception;

  public abstract @NotNull Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception;

  public abstract void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception;

  public abstract void updateTimeSpend(@NotNull LocalTask task, @NotNull String timeSpent, String comment) throws Exception;

  public final @NotNull String getVersionName() {
    return getType().getVersionName();
  }

  @Override
  public final String toString() {
    return "JiraRemoteApi(" + getType().getVersionName() + ")";
  }

  public abstract @NotNull ApiType getType();

  public enum ApiType {
    LEGACY("XML-RPC + RSS") {
      @Override
      public @NotNull JiraLegacyApi createApi(@NotNull JiraRepository repository) {
        return new JiraLegacyApi(repository);
      }
    },
    REST_2_0("REST 2.0") {
      @Override
      public @NotNull JiraRestApi2 createApi(@NotNull JiraRepository repository) {
        return new JiraRestApi2(repository);
      }
    },
    REST_2_0_ALPHA("REST 2.0.alpha1") {
      @Override
      public @NotNull JiraRestApi20Alpha1 createApi(@NotNull JiraRepository repository) {
        return new JiraRestApi20Alpha1(repository);
      }
    };

    ApiType(String versionName) {
      myVersionName = versionName;
    }
    private final String myVersionName;
    public abstract @NotNull JiraRemoteApi createApi(@NotNull JiraRepository repository);

    public @NotNull String getVersionName() {
      return myVersionName;
    }
  }
}
