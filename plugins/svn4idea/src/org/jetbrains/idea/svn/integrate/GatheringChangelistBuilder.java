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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.update.UpdatedFilesReverseSide;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GatheringChangelistBuilder implements ChangelistBuilder {
  private final Set<VirtualFile> myCheckSet;
  private final List<Change> myChanges;
  private final UpdatedFilesReverseSide myFiles;
  private final VirtualFile myMergeRoot;
  private final SvnVcs myVcs;

  public GatheringChangelistBuilder(final Project project, final UpdatedFilesReverseSide files, final VirtualFile mergeRoot) {
    myVcs = SvnVcs.getInstance(project);
    myFiles = files;
    myMergeRoot = mergeRoot;
    myChanges = new ArrayList<Change>();
    myCheckSet = new HashSet<VirtualFile>();
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

  private void addChange(final Change change) {
    final FilePath path = ChangesUtil.getFilePath(change);
    final VirtualFile vf = path.getVirtualFile();
    if ((mergeinfoChanged(path.getIOFile()) || ((vf != null) && myFiles.containsFile(vf))) && (! myCheckSet.contains(vf))) {
      myCheckSet.add(vf);
      myChanges.add(change);
    }
  }

  private boolean mergeinfoChanged(final File file) {
    final SVNWCClient client = myVcs.createWCClient();
    try {
      final SVNPropertyData current = client.doGetProperty(file, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
      final SVNPropertyData base = client.doGetProperty(file, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.BASE);
      if (current != null) {
        if (base == null) {
          return true;
        } else {
          final SVNPropertyValue currentValue = current.getValue();
          final SVNPropertyValue baseValue = base.getValue();
          return ! Comparing.equal(currentValue, baseValue);
        }
      }
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  public void processUnversionedFile(final VirtualFile file) {

  }

  public void processLocallyDeletedFile(final FilePath file) {

  }

  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
    
  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {

  }

  public void processIgnoredFile(final VirtualFile file) {

  }

  public void processLockedFolder(final VirtualFile file) {
  }

  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {

  }

  public void processRootSwitch(VirtualFile file, String branch) {
  }

  public boolean isUpdatingUnversionedFiles() {
    return false;
  }

  public boolean reportChangesOutsideProject() {
    return true;
  }

  public void reportWarningMessage(final String message) {
    // todo maybe, use further
  }

  public List<Change> getChanges() {
    return myChanges;
  }
}
