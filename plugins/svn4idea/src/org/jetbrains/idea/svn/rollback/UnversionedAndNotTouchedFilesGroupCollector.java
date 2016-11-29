/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.EmptyChangelistBuilder;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
* @author Konstantin Kolosovsky.
*/
public class UnversionedAndNotTouchedFilesGroupCollector extends EmptyChangelistBuilder {
  private final List<Couple<File>> myToBeDeleted;
  private final Map<File, ThroughRenameInfo> myFromTo;
  // created by changes
  private TreeMap<String, File> myRenames;
  private Set<String> myAlsoReverted;

  UnversionedAndNotTouchedFilesGroupCollector() {
    myFromTo = new HashMap<>();
    myToBeDeleted = new ArrayList<>();
  }

  @Override
  public void processUnversionedFile(final VirtualFile file) {
    toFromTo(file);
  }

  public void markRename(@NotNull final File beforeFile, @NotNull final File afterFile) {
    myToBeDeleted.add(Couple.of(beforeFile, afterFile));
  }

  public ThroughRenameInfo findToFile(@NotNull final FilePath file, @Nullable final File firstTo) {
    final String path = FilePathsHelper.convertPath(file);
    if (myAlsoReverted.contains(path)) return null;
    final NavigableMap<String, File> head = myRenames.headMap(path, true);
    if (head == null || head.isEmpty()) return null;
    for (Map.Entry<String, File> entry : head.descendingMap().entrySet()) {
      if (path.equals(entry.getKey())) return null;
      if (path.startsWith(entry.getKey())) {
        final String convertedBase = FileUtil.toSystemIndependentName(entry.getKey());
        final String convertedChild = FileUtil.toSystemIndependentName(file.getPath());
        final String relativePath = FileUtil.getRelativePath(convertedBase, convertedChild, '/');
        assert relativePath != null;
        return new ThroughRenameInfo(entry.getValue(), new File(entry.getValue(), relativePath), firstTo, file.getIOFile(), firstTo != null);
      }
    }
    return null;
  }

  private void toFromTo(VirtualFile file) {
    FilePath path = VcsUtil.getFilePath(file);
    final ThroughRenameInfo info = findToFile(path, null);
    if (info != null) {
      myFromTo.put(path.getIOFile(), info);
    }
  }

  private void processChangeImpl(final Change change) {
    if (change.getAfterRevision() != null) {
      final FilePath after = change.getAfterRevision().getFile();
      final ThroughRenameInfo info = findToFile(after, change.getBeforeRevision() == null ? null : change.getBeforeRevision().getFile().getIOFile());
      if (info != null) {
        myFromTo.put(after.getIOFile(), info);
      }
    }
  }

  @Override
  public void processChange(Change change, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processChangeInList(Change change, String changeListName, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processIgnoredFile(VirtualFile file) {
    // as with unversioned
    toFromTo(file);
  }

  public List<Couple<File>> getToBeDeleted() {
    return myToBeDeleted;
  }

  public Map<File, ThroughRenameInfo> getFromTo() {
    return myFromTo;
  }

  public void setRenamesMap(TreeMap<String, File> renames) {
    myRenames = renames;
  }

  public void setAlsoReverted(Set<String> alsoReverted) {
    myAlsoReverted = alsoReverted;
  }
}
