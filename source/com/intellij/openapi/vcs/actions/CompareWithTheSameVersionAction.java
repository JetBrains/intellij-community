package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;

public class CompareWithTheSameVersionAction extends AbstractShowDiffAction{
  protected VcsRevisionNumber getRevisionNumber(DiffProvider diffProvider, VirtualFile file) {
    return diffProvider.getCurrentRevision(file);
  }
}
