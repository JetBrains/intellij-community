/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Callable;

/**
 * This class describes bug-tracking server.
 * Do not forget to mark your implementation with {@link Tag} annotation to make it persistent.
 *
 * @see TaskRepositoryType
 * @see com.intellij.tasks.impl.BaseRepository
 * @author Dmitry Avdeev
 */
@Tag("server")
public abstract class TaskRepository {

  protected static final int NO_FEATURES = 0;
  public static final int BASIC_HTTP_AUTHORIZATION = 0x0001;
  public static final int LOGIN_ANONYMOUSLY = 0x0002;
  public static final int TIME_MANAGEMENT = 0x0004;
  public static final int STATE_UPDATING = 0x0008;
  /**
   * Supporting this feature means that server implements some kind of issues filtering.
   * It may be special query language like the one used in YouTrack or mere plain
   * text search.
   * <p>
   * If server supports this feature it MUST return tasks already filtered according
   * to {@code query} parameter from {@link #getIssues}} method, otherwise they will
   * be filtered internally in {@link TaskManager#getIssues}
   */
  public static final int NATIVE_SEARCH = 0x0010;

  @Attribute("url")
  public String getUrl() {
    return trimTrailingSlashes(myUrl);
  }

  public void setUrl(String url) {
    myUrl = trimTrailingSlashes(url);
  }

  public boolean isConfigured() {
    return StringUtil.isNotEmpty(getUrl());
  }

  @Attribute("shared")
  public boolean isShared() {
    return myShared;
  }

  public void setShared(boolean shared) {
    myShared = shared;
  }

  public String getPresentableName() {
    return StringUtil.isEmpty(getUrl()) ? "<undefined>" : getUrl();
  }

  public Icon getIcon() {
    return getRepositoryType().getIcon();
  }

  /**
   * @deprecated
   * @see #createCancellableConnection()
   */
  public void testConnection() throws Exception {
  }

  /**
   * Returns an object that can test connection.
   * {@link com.intellij.openapi.vcs.impl.CancellableRunnable#cancel()} should cancel the process.
   * @return null if not supported
   */
  @Nullable
  public CancellableConnection createCancellableConnection() {
    return null;
  }

  /**
   * Get issues from the repository. If query is null, return issues should assigned to current user only.
   * If server supports {@link #NATIVE_SEARCH} feature, tasks returned MUST be filtered by specified query.
   *
   * @param query repository specific.
   * @param max   maximum issues number to return
   * @param since last updated timestamp. If 0, all issues should be returned.
   * @return found issues
   * @throws Exception
   */
  public abstract Task[] getIssues(@Nullable String query, int max, long since) throws Exception;

  public Task[] getIssues(@Nullable String query, int max, long since, @NotNull ProgressIndicator cancelled) throws Exception {
    return getIssues(query, max, since);
  }

  @Nullable
  public abstract Task findTask(String id) throws Exception;

  public abstract TaskRepository clone();

  @Nullable
  public abstract String extractId(String taskName);

  /**
   * @see com.intellij.tasks.TaskRepositoryType#getPossibleTaskStates()
   */
  public void setTaskState(Task task, TaskState state) throws Exception {
    throw new UnsupportedOperationException();
  }

  // for serialization
  public TaskRepository() {
    myType = null;
  }

  public TaskRepository(TaskRepositoryType type) {
    myType = type;
  }

  protected TaskRepository(TaskRepository other) {
    myType = other.myType;
    myShared = other.isShared();
    myUrl = other.getUrl();
    setShouldFormatCommitMessage(other.myShouldFormatCommitMessage);
    setCommitMessageFormat(other.myCommitMessageFormat);
  }

  private boolean myShared;
  private String myUrl = "";
  private TaskRepositoryType myType;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaskRepository)) return false;

    TaskRepository that = (TaskRepository)o;
    if (!Comparing.equal(myType, that.myType)) return false;
    if (isShared() != that.isShared()) return false;
    if (getUrl() != null ? !getUrl().equals(that.getUrl()) : that.getUrl() != null) return false;
    if (getCommitMessageFormat() != null ? !getCommitMessageFormat().equals(that.getCommitMessageFormat()) : that.getCommitMessageFormat() != null) return false;
    return isShouldFormatCommitMessage() == that.isShouldFormatCommitMessage();
  }

  @Transient
  public final TaskRepositoryType getRepositoryType() {
    return myType;
  }

  public final void setRepositoryType(TaskRepositoryType type) {
    myType = type;
  }

  public boolean isShouldFormatCommitMessage() {
    return myShouldFormatCommitMessage;
  }

  public void setShouldFormatCommitMessage(final boolean shouldFormatCommitMessage) {
    myShouldFormatCommitMessage = shouldFormatCommitMessage;
  }

  @Tag("commitMessageFormat")
  public String getCommitMessageFormat() {
    return myCommitMessageFormat;
  }

  public void setCommitMessageFormat(final String commitMessageFormat) {
    myCommitMessageFormat = commitMessageFormat;
  }

  protected boolean myShouldFormatCommitMessage;
  protected String myCommitMessageFormat = "{id} {summary}";

  private static String trimTrailingSlashes(String url) {
    if (url == null) return "";
    for (int i = url.length() - 1; i >= 0; i--) {
      if (url.charAt(i) != '/') {
        return url.substring(0, i + 1);
      }
    }
    return "";
  }

  @Nullable
  public String getTaskComment(Task task) {
    return isShouldFormatCommitMessage()
           ? myCommitMessageFormat.replace("{id}", task.getId()).replace("{summary}", task.getSummary())
           : null;
  }

  public String getComment() {
    return "{id} (e.g. FOO-001), {summary}, {number} (e.g. 001), {project} (e.g. FOO)";
  }

  public void updateTimeSpent(final LocalTask task, final String timeSpent, final String comment) throws Exception {
    throw new UnsupportedOperationException();
  }

  public abstract static class CancellableConnection implements Callable<Exception> {

    @Nullable
    @Override
    public final Exception call() {
      try {
        doTest();
        return null;
      }
      catch (Exception e) {
        return e;
      }
    }

    protected abstract void doTest() throws Exception;

    public abstract void cancel();
  }

  public boolean isSupported(
    @MagicConstant(
      flags = {
        NO_FEATURES,
        BASIC_HTTP_AUTHORIZATION,
        LOGIN_ANONYMOUSLY,
        STATE_UPDATING,
        TIME_MANAGEMENT,
        NATIVE_SEARCH}
    ) int feature) {
    return (getFeatures() & feature) != 0;
  }

  @MagicConstant(
    flags = {
      NO_FEATURES,
      BASIC_HTTP_AUTHORIZATION,
      LOGIN_ANONYMOUSLY,
      STATE_UPDATING,
      TIME_MANAGEMENT,
      NATIVE_SEARCH})
  protected int getFeatures() {
    return NATIVE_SEARCH;
  }
}
