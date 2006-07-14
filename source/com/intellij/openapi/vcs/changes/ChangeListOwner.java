package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author max
 */
public interface ChangeListOwner {
  void moveChangesTo(LocalChangeList list, Change[] changes);
  void addUnversionedFiles(final LocalChangeList list, final List<VirtualFile> unversionedFiles);
}
