/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public abstract class ChangeListManager implements ChangeListModification {
  public static ChangeListManager getInstance(Project project) {
    return project.getComponent(ChangeListManager.class);
  }

  public static ChangeListManager getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ChangeListManager>() {
      public ChangeListManager compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return project.getComponent(ChangeListManager.class);
      }
    });
  }

  public abstract void scheduleUpdate();
  public abstract void scheduleUpdate(boolean updateUnversionedFiles);
  public abstract void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title,
                                         final boolean synchronous);
  public abstract boolean ensureUpToDate(boolean canBeCanceled);

  public abstract List<LocalChangeList> getChangeListsCopy();
  @NotNull
  public abstract List<LocalChangeList> getChangeLists();

  public abstract List<File> getAffectedPaths();
  @NotNull
  public abstract List<VirtualFile> getAffectedFiles();
  public abstract boolean isFileAffected(final VirtualFile file);

  @Nullable
  public abstract LocalChangeList findChangeList(final String name);
//  public abstract LocalChangeList addChangeList(@NotNull String name, final String comment);
//  public abstract void setDefaultChangeList(@NotNull LocalChangeList list);
  @Nullable
  public abstract LocalChangeList getDefaultChangeList();

  @Nullable
  public abstract LocalChangeList getChangeList(Change change);

  @NotNull
  public abstract Runnable prepareForChangeDeletion(final Collection<Change> changes);

  @Nullable
  public abstract Change getChange(VirtualFile file);

  @Nullable
  public abstract Change getChange(FilePath file);

  public abstract boolean isUnversioned(VirtualFile file);

  @NotNull
  public abstract FileStatus getStatus(VirtualFile file);

  @NotNull
  public abstract Collection<Change> getChangesIn(VirtualFile dir);

  @NotNull
  public abstract Collection<Change> getChangesIn(FilePath path);

//  public abstract void removeChangeList(final LocalChangeList list);

//  public abstract void moveChangesTo(final LocalChangeList list, final Change[] changes);

  public abstract void addChangeListListener(ChangeListListener listener);
  public abstract void removeChangeListListener(ChangeListListener listener);

  public abstract void registerCommitExecutor(CommitExecutor executor);
  
  public abstract void commitChanges(LocalChangeList changeList, List<Change> changes);

  /**
   *
   * @param changeList
   * @param changes
   * @return if commit successful
   */
  public abstract void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes);

  /**
   *
   * @param changeList
   * @param changes
   * @return if commit successful
   */
  public abstract boolean commitChangesSynchronouslyWithResult(LocalChangeList changeList, List<Change> changes);

  public abstract void reopenFiles(List<FilePath> paths);

  public abstract List<CommitExecutor> getRegisteredExecutors();

  public abstract void addFilesToIgnore(final IgnoredFileBean... ignoredFiles);
  public abstract void setFilesToIgnore(final IgnoredFileBean... ignoredFiles);
  public abstract IgnoredFileBean[] getFilesToIgnore();
  public abstract boolean isIgnoredFile(@NotNull VirtualFile file);

  @Nullable
  public abstract String getSwitchedBranch(VirtualFile file);
  public abstract String getDefaultListName();
}
