/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.update.UpdatedFilesReverseSide;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;

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

  public void processChange(final Change change, VcsKey vcsKey) {
    addChange(change);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList, VcsKey vcsKey) {
    addChange(change);
  }

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
    SvnTarget target = SvnTarget.fromFile(file);

    try {
      SVNPropertyData current =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, SVNRevision.WORKING);
      SVNPropertyData base =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, SVNRevision.BASE);

      if (current != null) {
        return base == null || !Comparing.equal(current.getValue(), base.getValue());
      }
    }
    catch (VcsException e) {
      LOG.info(e);
    }
    return false;
  }

  public boolean reportChangesOutsideProject() {
    return true;
  }

  @NotNull
  public List<Change> getChanges() {
    return myChanges;
  }
}
