package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GatheringChangelistBuilder implements ChangelistBuilder {
  private final List<Change> myChanges;

  public GatheringChangelistBuilder() {
    myChanges = new ArrayList<Change>();
  }

  public void processChange(final Change change) {
    myChanges.add(change);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList) {
    myChanges.add(change);
  }

  public void processChangeInList(final Change change, final String changeListName) {
    myChanges.add(change);
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
