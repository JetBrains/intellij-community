package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.wc.SVNStatusClient;
import com.intellij.openapi.vfs.VirtualFile;

public interface StatusWalkerPartner {
  SVNStatusClient createStatusClient();
  void checkCanceled();
  boolean isExcluded(final VirtualFile vFile);
  boolean isIgnoredIdeaLevel(final VirtualFile vFile);
}
