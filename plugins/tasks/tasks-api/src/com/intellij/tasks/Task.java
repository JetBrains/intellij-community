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
   * Task identifier, e.g. IDEA-00001
   * @return unique id
   */
  @NotNull
  public abstract String getId();

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
      text = getId() + ": " + getSummary();
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

  @NotNull
  public String getNumber() {
    int i = getId().lastIndexOf('-');
    return i > 0 ? getId().substring(i + 1) : getId();
  }

  @Nullable
  public String getProject() {
    int i = getId().lastIndexOf('-');
    return i > 0 ? getId().substring(0, i) : null;
  }
}
