package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;

public class RootMixedInfo {
  /**
   * working copy root path
   */
  private final String myFilePath;
  private final VirtualFile myFile;
  /**
   * url corresponding to working copy root
   */
  private final SVNURL myUrl;
  private final VirtualFile myParentVcsRoot;

  public RootMixedInfo(final String filePath, final VirtualFile file, final SVNURL url, final VirtualFile parentVcsRoot) {
    myFilePath = filePath;
    myFile = file;
    myUrl = url;
    myParentVcsRoot = parentVcsRoot;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public VirtualFile getParentVcsRoot() {
    return myParentVcsRoot;
  }
}
