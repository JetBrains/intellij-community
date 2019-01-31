// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnDiffProvider;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;

public class SvnFileAnnotation extends BaseSvnFileAnnotation {
  private final VirtualFile myFile;

  public SvnFileAnnotation(SvnVcs vcs, VirtualFile file, String contents, VcsRevisionNumber baseRevision) {
    super(vcs, contents, baseRevision);
    myFile = file;
  }

  @Override
  public void dispose() {
  }

  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  protected void showAllAffectedPaths(SvnRevisionNumber number) {
    ShowAllAffectedGenericAction.showSubmittedFiles(myVcs.getProject(), number, myFile, myVcs.getKeyInstanceMethod());
  }

  @Override
  public boolean isBaseRevisionChanged(VcsRevisionNumber number) {
    final VcsRevisionDescription description = ((SvnDiffProvider)myVcs.getDiffProvider()).getCurrentRevisionDescription(myFile);
    return description != null && ! description.getRevisionNumber().equals(myBaseRevision);
  }
}
