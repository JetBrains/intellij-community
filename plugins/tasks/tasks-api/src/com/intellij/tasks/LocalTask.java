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

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class LocalTask extends Task {

  public abstract void setUpdated(Date date);

  public abstract void setActive(boolean active);

  /**
   * @return true if the task is currently active
   */
  @Attribute("active")
  public abstract boolean isActive();

  public abstract void updateFromIssue(Task issue);

  public boolean isDefault() {
    return false;
  }

  @NotNull
  public abstract List<ChangeListInfo> getChangeLists();

  public abstract void addChangelist(ChangeListInfo info);

  public abstract void removeChangelist(final ChangeListInfo info);

  public abstract long getTimeSpent();

  public abstract void setTimeSpent(long time);

  public abstract boolean isRunning();

  public abstract void setRunning(final boolean running);
}
