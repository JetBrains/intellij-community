package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import org.tmatesoft.svn.core.wc.SVNStatus;

class SvnChangedFile {
  private final FilePath myFilePath;
  private final SVNStatus myStatus;
  private String myCopyFromURL;

  public SvnChangedFile(final FilePath filePath, final SVNStatus status) {
    myFilePath = filePath;
    myStatus = status;
  }

  public SvnChangedFile(final FilePath filePath, final SVNStatus status, final String copyFromURL) {
    myFilePath = filePath;
    myStatus = status;
    myCopyFromURL = copyFromURL;
  }

  public FilePath getFilePath() {
    return myFilePath;
  }

  public SVNStatus getStatus() {
    return myStatus;
  }

  public String getCopyFromURL() {
    if (myCopyFromURL == null) {
      return myStatus.getCopyFromURL();
    }
    return myCopyFromURL;
  }

  @Override
  public String toString() {
    return myFilePath.getPath();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnChangedFile that = (SvnChangedFile)o;

    if (myFilePath != null ? !myFilePath.equals(that.myFilePath) : that.myFilePath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFilePath != null ? myFilePath.hashCode() : 0;
  }
}
