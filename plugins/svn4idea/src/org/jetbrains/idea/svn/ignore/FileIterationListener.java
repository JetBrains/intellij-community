package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.vfs.VirtualFile;

public interface FileIterationListener {
  void onFileEnabled(final VirtualFile file);
}
