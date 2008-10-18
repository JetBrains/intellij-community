package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeListsIndexes {
  private final Map<String, FileStatus> myFileToStatus;

  ChangeListsIndexes() {
    myFileToStatus = new HashMap<String, FileStatus>();
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myFileToStatus = new HashMap<String, FileStatus>(idx.myFileToStatus);
  }

  void add(final FilePath file, final FileStatus status) {
    myFileToStatus.put(file.getIOFile().getAbsolutePath(), status);
  }

  void remove(final FilePath file) {
    myFileToStatus.remove(file.getIOFile().getAbsolutePath());
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myFileToStatus.get(new File(file.getPath()).getAbsolutePath());
  }

  public void changeAdded(final Change change) {
    addChangeToIdx(change);
  }

  public void changeRemoved(final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();

    if (afterRevision != null) {
      remove(afterRevision.getFile());
    }
    if (beforeRevision != null) {
      remove(beforeRevision.getFile());
    }
  }

  private void addChangeToIdx(final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus());
    }
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      if (afterRevision != null) {
        if (! Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
          add(beforeRevision.getFile(), FileStatus.DELETED);
        }
      } else {
        add(beforeRevision.getFile(), change.getFileStatus());
      }
    }
  }

  public List<File> getAffectedPaths() {
    final List<File> result = new ArrayList<File>(myFileToStatus.size());
    for (String path : myFileToStatus.keySet()) {
      result.add(new File(path));
    }
    return result;
  }
}
