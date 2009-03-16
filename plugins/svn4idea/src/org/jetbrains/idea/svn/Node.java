package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;

class Node {
  private final VirtualFile myFile;
  private final String myUrl;

  Node(final VirtualFile file, final String url) {
    myFile = file;
    myUrl = url;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public String getUrl() {
    return myUrl;
  }
}
