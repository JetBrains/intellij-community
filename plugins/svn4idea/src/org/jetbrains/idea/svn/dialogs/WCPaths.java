package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;

public interface WCPaths {
  String getRootUrl();
  String getRepoUrl();
  String getPath();
  VirtualFile getVcsRoot();
}
