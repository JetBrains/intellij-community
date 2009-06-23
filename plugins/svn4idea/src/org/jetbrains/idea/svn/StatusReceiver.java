package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.util.EventListener;

public interface StatusReceiver extends EventListener {
  void process(final FilePath path, final SVNStatus status, final boolean isInnerCopyRoot) throws SVNException;
  void processIgnored(final VirtualFile vFile);
  void processUnversioned(final VirtualFile vFile);
}
