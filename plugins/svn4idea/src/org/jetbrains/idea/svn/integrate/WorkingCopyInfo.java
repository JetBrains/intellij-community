package org.jetbrains.idea.svn.integrate;

public class WorkingCopyInfo {
  private final String myLocalPath;
  private final boolean myUnderProjectRoot;

  public WorkingCopyInfo(final String localPath, final boolean underProjectRoot) {
    myLocalPath = localPath;
    myUnderProjectRoot = underProjectRoot;
  }

  public String getLocalPath() {
    return myLocalPath;
  }

  public boolean isUnderProjectRoot() {
    return myUnderProjectRoot;
  }

  public String toString() {
    return myLocalPath;
  }
}
