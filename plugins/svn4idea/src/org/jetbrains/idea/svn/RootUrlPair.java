package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;

public interface RootUrlPair {
  VirtualFile getVirtualFile();
  String getUrl();
}
