package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.List;

public class WCInfoWithBranches extends WCInfo {
  private final List<Branch> myBranches;
  private final VirtualFile myRoot;

  public WCInfoWithBranches(final String path, final SVNURL url, final WorkingCopyFormat format, final String repositoryRoot,
                            final List<Branch> branches, final VirtualFile root) {
    super(path, url, format, repositoryRoot);
    myBranches = branches;
    myRoot = root;
  }

  // to be used in combo
  @Override
  public String toString() {
    return getPath();
  }

  public List<Branch> getBranches() {
    return myBranches;
  }

  public VirtualFile getRoot() {
    return myRoot;
  }

  public static class Branch {
    private final String myName;
    private final String myUrl;

    public Branch(final String url) {
      myName = SVNPathUtil.tail(url);
      myUrl = url;
    }

    public String getName() {
      return myName;
    }

    public String getUrl() {
      return myUrl;
    }

    @Override
    public String toString() {
      return myUrl;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Branch branch = (Branch)o;

      if (myUrl != null ? !myUrl.equals(branch.myUrl) : branch.myUrl != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return (myUrl != null ? myUrl.hashCode() : 0);
    }
  }
}
