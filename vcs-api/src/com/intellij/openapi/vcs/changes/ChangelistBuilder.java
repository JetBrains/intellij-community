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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Builder for the changes list in the file system. The instances of
 * this class are used to collect changes that happened in the file system.
 *
 * @see com.intellij.openapi.vcs.changes.ChangeProvider#getChanges(VcsDirtyScope, ChangelistBuilder, com.intellij.openapi.progress.ProgressIndicator)
 * @author max
 */
public interface ChangelistBuilder {
  /**
   * Process a change to the file. This method is used to report changes that
   * version control system knows about.
   *
   * @param change a change to process.
   */
  void processChange(Change change);

  void processChangeInList(Change change, @Nullable ChangeList changeList);

  /**
   * Put the given change into the change list with the given name.
   * If there is no such change list it is created.
   * This method allows not to refer to ChangeListManager for the LocalChangeList object.
   *
   * @param change         Submitted change
   * @param changeListName A name for a change list.
   */
  void processChangeInList(Change change, String changeListName);

  /**
   * Process a file that is not under version control.
   *
   * @param file a file to process
   */
  void processUnversionedFile(VirtualFile file);

  /**
   * Process a file that was deleted locally, but version
   * control has not been notified about removal yet.
   *
   * @param file a file to process
   */
  void processLocallyDeletedFile(FilePath file);

  /**
   * Process the file that was modified without explicit checkout
   * (if version control supports such behavior).
   *
   * @param file a file to process
   */
  void processModifiedWithoutCheckout(VirtualFile file);

  /**
   * Process the file that is ignored by the version control.
   *
   * @param file an ignored file
   */
  void processIgnoredFile(VirtualFile file);

  void processLockedFolder(VirtualFile file);

  /**
   * Report a file which has been updated to a branch other than that of the files around it
   * ("switched"). Changed files (reported through {@link #processChange}) can also be reported as switched.
   *
   * @param file      the switched file
   * @param branch    the name of the branch to which the file is switched.
   * @param recursive if true, all subdirectories of file are also marked as switched to that branch
   */
  void processSwitchedFile(VirtualFile file, String branch, final boolean recursive);

  boolean isUpdatingUnversionedFiles();
}
