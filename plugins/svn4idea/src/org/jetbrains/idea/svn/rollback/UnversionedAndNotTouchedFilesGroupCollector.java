// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.EmptyChangelistBuilder;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

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
  public void processUnversionedFile(@NotNull FilePath file) {
    toFromTo(file);
  }

  public void markRename(final @NotNull File beforeFile, final @NotNull File afterFile) {
    myToBeDeleted.add(Couple.of(beforeFile, afterFile));
  }

  public ThroughRenameInfo findToFile(final @NotNull FilePath file, final @Nullable File firstTo) {
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

  private void toFromTo(@NotNull FilePath path) {
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
  public void processChange(@NotNull Change change, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, String changeListName, VcsKey vcsKey) {
    processChangeImpl(change);
  }

  @Override
  public void processIgnoredFile(@NotNull FilePath file) {
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
