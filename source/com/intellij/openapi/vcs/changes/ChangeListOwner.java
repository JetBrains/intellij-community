package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public interface ChangeListOwner {
  void moveChangesTo(LocalChangeList list, Change[] changes);
  void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> unversionedFiles);
}
