package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.wc.SVNInfo;
import com.intellij.openapi.vfs.VirtualFile;

class Real extends Node implements RootUrlPair {
  private final SVNInfo myInfo;
  private final VirtualFile myVcsRoot;

  Real(final VirtualFile file, final SVNInfo info, VirtualFile vcsRoot) {
    super(file, info.getURL().toString());
    myInfo = info;
    myVcsRoot = vcsRoot;
  }

  public SVNInfo getInfo() {
    return myInfo;
  }

  public VirtualFile getVcsRoot() {
    return myVcsRoot;
  }

  public VirtualFile getVirtualFile() {
    return getFile();
  }
}
