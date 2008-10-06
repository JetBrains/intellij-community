package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class ChangeListsIndexes implements ChangeListWorker.LocalListListener {
  private final Map<VirtualFile, String> myFileToListName;
  private final Map<VirtualFile, FileStatus> myFileToStatus;

  ChangeListsIndexes() {
    myFileToListName = new HashMap<VirtualFile, String>();
    myFileToStatus = new HashMap<VirtualFile, FileStatus>();
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myFileToListName = new HashMap<VirtualFile, String>(idx.myFileToListName);
    myFileToStatus = new HashMap<VirtualFile, FileStatus>(idx.myFileToStatus);
  }

  void add(final VirtualFile file, final String listName, final FileStatus status) {
    myFileToListName.put(file, listName);
    myFileToStatus.put(file, status);
  }

  void remove(final VirtualFile file) {
    myFileToListName.remove(file);
    myFileToStatus.remove(file);
  }

  void move(final VirtualFile file, final String newListName) {
    myFileToListName.put(file, newListName);
  }

  public String getListName(final VirtualFile file) {
    return myFileToListName.get(file);
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myFileToStatus.get(file);
  }

  void renamed(final String newName, final Collection<Change> changes) {
    for (Change change : changes) {
      addChangeToIdx(newName, change);
    }
  }

  public void changeAdded(final String listName, final Change change) {
    addChangeToIdx(listName, change);
  }

  public void changeRemoved(final String listName, final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if ((isInIdx(afterRevision, listName)) || isInIdx(beforeRevision, listName)) {
      if (afterRevision != null) {
        remove(afterRevision.getFile().getVirtualFile());
      }
      if (beforeRevision != null) {
        remove(beforeRevision.getFile().getVirtualFile());
      }
    }
  }

  private boolean isInIdx(final ContentRevision revision, final String listName) {
    if (revision != null) {
      final VirtualFile vf = revision.getFile().getVirtualFile();
      final String idxListName = myFileToListName.get(vf);
      return Comparing.equal(idxListName, listName);
    }
    return false;
  }

  private void addChangeToIdx(final String listName, final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      add(afterRevision.getFile().getVirtualFile(), listName, change.getFileStatus());
    }
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      if (afterRevision != null) {
        if (! Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
          add(beforeRevision.getFile().getVirtualFile(), listName, FileStatus.DELETED);
        }
      } else {
        add(beforeRevision.getFile().getVirtualFile(), listName, change.getFileStatus());
      }
    }
  }

  public List<File> getAffectedPaths() {
    final List<File> result = new ArrayList<File>(myFileToListName.size());
    for (VirtualFile virtualFile : myFileToListName.keySet()) {
      result.add(new File(virtualFile.getPath()));
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    return new ArrayList<VirtualFile>(myFileToListName.keySet());
  }
}
