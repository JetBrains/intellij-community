package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;

public interface SelectedCommittedStuffChecker {
  void execute(final AnActionEvent event);

  boolean isValid();

  SVNURL getSameBranch();

  VirtualFile getRoot();

  MergerFactory createFactory();
}
