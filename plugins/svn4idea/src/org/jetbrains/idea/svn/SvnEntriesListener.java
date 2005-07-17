package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;

public interface SvnEntriesListener {
  void onEntriesChanged(VirtualFile directory);
}
