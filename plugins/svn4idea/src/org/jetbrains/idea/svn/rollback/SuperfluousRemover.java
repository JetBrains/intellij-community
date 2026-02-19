// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SuperfluousRemover {

  private final Set<File> myParentPaths;
  private final boolean myCheckBefore;

  SuperfluousRemover(boolean checkBefore) {
    myCheckBefore = checkBefore;
    myParentPaths = new HashSet<>();
  }

  protected boolean accept(@NotNull Change change) {
    ContentRevision mainRevision = myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();
    ContentRevision otherRevision = !myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();

    if (mainRevision == null || SvnRollbackEnvironment.isMoveRenameReplace(change)) {
      check(otherRevision.getFile().getIOFile());
      return true;
    }

    return false;
  }

  public void check(final @NotNull File file) {
    boolean parentAlreadyRegistered =
      ContainerUtil.or(myParentPaths, parentCandidate -> VfsUtilCore.isAncestor(parentCandidate, file, true));

    if (!parentAlreadyRegistered) {
      ContainerUtil.retainAll(myParentPaths, childCandidate -> !VfsUtilCore.isAncestor(file, childCandidate, true));

      myParentPaths.add(file);
    }
  }

  public Set<File> getParentPaths() {
    return myParentPaths;
  }
}
