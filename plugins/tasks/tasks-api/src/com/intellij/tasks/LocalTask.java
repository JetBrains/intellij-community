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

import com.intellij.openapi.util.Condition;
import com.intellij.tasks.timeTracking.model.WorkItem;
import com.intellij.util.containers.ContainerUtil;
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

  // VCS interface

  @NotNull
  public abstract List<ChangeListInfo> getChangeLists();

  public abstract void addChangelist(ChangeListInfo info);

  public abstract void removeChangelist(final ChangeListInfo info);

  /**
   * For serialization only.
   * @return two branches per repository: feature-branch itself and original branch to merge into
   * @see #getBranches(boolean)
   */
  @NotNull
  public abstract List<BranchInfo> getBranches();

  @NotNull
  public List<BranchInfo> getBranches(final boolean original) {
    return ContainerUtil.filter(getBranches(), new Condition<BranchInfo>() {
      @Override
      public boolean value(BranchInfo info) {
        return info.original == original;
      }
    });
  }

  public abstract void addBranch(BranchInfo info);

  public abstract void removeBranch(final BranchInfo info);

  // time tracking interface

  public abstract long getTotalTimeSpent();

  public abstract boolean isRunning();

  public abstract void setRunning(final boolean running);

  public abstract void setWorkItems(List<WorkItem> workItems);

  public abstract List<WorkItem> getWorkItems();

  public abstract void addWorkItem(WorkItem workItem);

  public abstract Date getLastPost();

  public abstract void setLastPost(Date date);

  public abstract long getTimeSpentFromLastPost();
}
