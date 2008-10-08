package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.update.UpdatedFilesReverseSide;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GatheringChangelistBuilder implements ChangelistBuilder {
  private final Set<VirtualFile> myCheckSet;
  private final List<Change> myChanges;
  private final UpdatedFilesReverseSide myFiles;
  private final VirtualFile myMergeRoot;

  public GatheringChangelistBuilder(final UpdatedFilesReverseSide files, final VirtualFile mergeRoot) {
    myFiles = files;
    myMergeRoot = mergeRoot;
    myChanges = new ArrayList<Change>();
    myCheckSet = new HashSet<VirtualFile>();
  }

  public void processChange(final Change change) {
    addChange(change);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList) {
    addChange(change);
  }

  public void processChangeInList(final Change change, final String changeListName) {
    addChange(change);
  }

  private void addChange(final Change change) {
    final FilePath path = ChangesUtil.getFilePath(change);
    final VirtualFile vf = path.getVirtualFile();
    if ((Comparing.equal(myMergeRoot, vf) || myFiles.containsFile(vf)) && (! myCheckSet.contains(vf))) {
      myCheckSet.add(vf);
      myChanges.add(change);
    }
  }

  public void processUnversionedFile(final VirtualFile file) {

  }

  public void processLocallyDeletedFile(final FilePath file) {

  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {

  }

  public void processIgnoredFile(final VirtualFile file) {

  }

  public void processLockedFolder(final VirtualFile file) {
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {

  }

  public boolean isUpdatingUnversionedFiles() {
    return false;
  }

  public List<Change> getChanges() {
    return myChanges;
  }
}
