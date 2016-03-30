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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public abstract class Task {

  public static Task[] EMPTY_ARRAY = new Task[0];

  /**
   * Global unique task identifier, e.g. IDEA-00001. It's important that its format is consistent with
   * {@link TaskRepository#extractId(String)}, because otherwise task won't be updated on its activation.
   * Note that this ID is used to find issues and to compare them, so (ideally) it has to be unique.
   * 
   * In some cases task server doesn't offer such global ID (but, for instance, pair (project-name, per-project-id) instead) or it's not
   * what users want to see in UI (e.g. notorious <tt>id</tt> and <tt>iid</tt> in Gitlab). In this case you should generate artificial ID 
   * for internal usage and implement {@link #getPresentableId()}.
   *
   * @return unique global ID as described
   *
   * @see #getPresentableId()
   * @see TaskRepository#extractId(String)
   * @see TaskManager#activateTask(Task, boolean)
   */
  @NotNull
  public abstract String getId();


  /**
   * @return ID in the form that is suitable for commit messages, dialogs, completion items, etc.
   */
  @NotNull
  public String getPresentableId() {
    return getId();
  }
  /**
   * Short task description.
   * @return description
   */
  @NotNull
  public abstract String getSummary();

  @Nullable
  public abstract String getDescription();

  @NotNull
  public abstract Comment[] getComments();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public abstract TaskType getType();

  @Nullable
  public abstract Date getUpdated();

  @Nullable
  public abstract Date getCreated();

  public abstract boolean isClosed();

  @Nullable
  public String getCustomIcon() {
    return null;
  }

  /**
   * @return true if bugtracking issue is associated
   */
  public abstract boolean isIssue();

  @Nullable
  public abstract String getIssueUrl();

  /**
   * @return null if no issue is associated
   * @see #isIssue()
   */
  @Nullable
  public TaskRepository getRepository() {
    return null;
  }

  @Nullable
  public TaskState getState() {
    return null;
  }

  @Override
  public final String toString() {
    String text;
    if (isIssue()) {
      text = getPresentableId() + ": " + getSummary();
    } else {
      text = getSummary();
    }
    return StringUtil.first(text, 60, true);
  }

  public String getPresentableName() {
    return toString();
  }

  @Override
  public final boolean equals(Object obj) {
    return obj instanceof Task && ((Task)obj).getId().equals(getId());
  }

  @Override
  public final int hashCode() {
    return getId().hashCode();
  }

  /**
   * <b>Per-project</b> issue identifier. Default behavior is to extract project name from task's ID.
   * If your service doesn't provide issue ID in format <tt>PROJECT-123</tt> be sure to initialize it manually,
   * as it will be used to format commit messages.
   *
   * @return project-wide issue identifier
   *
   * @see #getId()
   * @see TaskRepository#getCommitMessageFormat()
   */
  @NotNull
  public String getNumber() {
    return extractNumberFromId(getId());
  }

  @NotNull
  protected static String extractNumberFromId(@NotNull String id) {
    int i = id.lastIndexOf('-');
    return i > 0 ? id.substring(i + 1) : id;
  }

  /**
   * Name of the project task belongs to. Default behavior is to extract project name from task's ID.
   * If your service doesn't provide issue ID in format <tt>PROJECT-123</tt> be sure to initialize it manually,
   * as it will be used to format commit messages.
   *
   * @return name of the project
   *
   * @see #getId()
   * @see TaskRepository#getCommitMessageFormat()
   */
  @Nullable
  public String getProject() {
    return extractProjectFromId(getId());
  }

  @Nullable
  protected static String extractProjectFromId(@NotNull String id) {
    int i = id.lastIndexOf('-');
    return i > 0 ? id.substring(0, i) : null;
  }
}
