// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class GatheringChangelistBuilder extends EmptyChangelistBuilder {

  private static final Logger LOG = Logger.getInstance(GatheringChangelistBuilder.class);

  @NotNull private final Set<VirtualFile> myCheckSet;
  @NotNull private final List<Change> myChanges;
  @NotNull private final UpdatedFilesReverseSide myFiles;
  @NotNull private final SvnVcs myVcs;

  public GatheringChangelistBuilder(@NotNull SvnVcs vcs, @NotNull UpdatedFilesReverseSide files) {
    myVcs = vcs;
    myFiles = files;
    myChanges = ContainerUtil.newArrayList();
    myCheckSet = ContainerUtil.newHashSet();
  }

  @Override
  public void processChange(final Change change, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void processChangeInList(final Change change, @Nullable final ChangeList changeList, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void processChangeInList(final Change change, final String changeListName, VcsKey vcsKey) {
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

  @NotNull
  public List<Change> getChanges() {
    return myChanges;
  }
}
