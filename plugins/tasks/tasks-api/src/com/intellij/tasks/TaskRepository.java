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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * @author Dmitry Avdeev
 */
@Tag("server")
public abstract class TaskRepository  {

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

  /**
   * @deprecated
   * @see #createCancellableConnection()
   */
  public void testConnection() throws Exception {}

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
   *
   * @param query repository specific.
   * @param max maximum issues number to return
   * @param since last updated timestamp. If 0, all issues should be returned.
   * @return found issues
   * @throws Exception
   */
  public abstract Task[] getIssues(@Nullable String query, int max, long since) throws Exception;

  @Nullable
  public abstract Task findTask(String id) throws Exception;

  public abstract TaskRepository clone();

  @Nullable
  public abstract String extractId(String taskName);

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

    return true;
  }

  @Transient
  public TaskRepositoryType getRepositoryType() {
    return myType;
  }

  public void setRepositoryType(TaskRepositoryType type) {
    myType = type;
  }

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
    return null;
  }

  public abstract class CancellableConnection implements Callable<Exception> {

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

}
