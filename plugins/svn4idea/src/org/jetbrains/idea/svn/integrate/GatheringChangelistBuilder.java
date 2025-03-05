// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.EmptyChangelistBuilder;
import com.intellij.openapi.vcs.update.UpdatedFilesReverseSide;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.*;

public class GatheringChangelistBuilder extends EmptyChangelistBuilder {

  private static final Logger LOG = Logger.getInstance(GatheringChangelistBuilder.class);

  private final @NotNull Set<VirtualFile> myCheckSet;
  private final @NotNull List<Change> myChanges;
  private final @NotNull UpdatedFilesReverseSide myFiles;
  private final @NotNull SvnVcs myVcs;

  public GatheringChangelistBuilder(@NotNull SvnVcs vcs, @NotNull UpdatedFilesReverseSide files) {
    myVcs = vcs;
    myFiles = files;
    myChanges = new ArrayList<>();
    myCheckSet = new HashSet<>();
  }

  @Override
  public void processChange(@NotNull Change change, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, String changeListName, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
    // not sure
    for (Iterator<Change> iterator = myChanges.iterator(); iterator.hasNext(); ) {
      final Change change = iterator.next();
      if (path.equals(ChangesUtil.getFilePath(change))) {
        final VirtualFile vf = path.getVirtualFile();
        if (vf != null) {
          myCheckSet.remove(vf);
          iterator.remove();
          return;
        }
      }
    }
  }

  private void addChange(final Change change) {
    final FilePath path = ChangesUtil.getFilePath(change);
    final VirtualFile vf = path.getVirtualFile();
    if ((mergeInfoChanged(path.getIOFile()) || (vf != null && myFiles.containsFile(vf))) && !myCheckSet.contains(vf)) {
      myCheckSet.add(vf);
      myChanges.add(change);
    }
  }

  private boolean mergeInfoChanged(final File file) {
    Target target = Target.on(file);

    try {
      PropertyValue current =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, Revision.WORKING);
      PropertyValue base =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, Revision.BASE);

      if (current != null) {
        return base == null || !Comparing.equal(current, base);
      }
    }
    catch (VcsException e) {
      LOG.info(e);
    }
    return false;
  }

  @Override
  public boolean reportChangesOutsideProject() {
    return true;
  }

  public @NotNull List<Change> getChanges() {
    return myChanges;
  }
}
