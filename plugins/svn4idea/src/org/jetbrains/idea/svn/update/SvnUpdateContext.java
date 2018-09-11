// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.util.FilePathByPathComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.FilePathUtil;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.util.*;

public class SvnUpdateContext implements SequentialUpdatesContext {
  private final Set<File> myUpdatedExternals;
  private final SvnVcs myVcs;
  private final List<FilePath> myContentRoots;

  public SvnUpdateContext(final SvnVcs vcs, FilePath[] contentRoots) {
    myVcs = vcs;
    myContentRoots = Arrays.asList(contentRoots);
    Collections.sort(myContentRoots, FilePathByPathComparator.getInstance());
    myUpdatedExternals = new HashSet<>();
  }

  @Override
  @NotNull
  public String getMessageWhenInterruptedBeforeStart() {
    // never
    return null;
  }

  @Override
  public boolean shouldFail() {
    return false;
  }

  public void registerExternalRootBeingUpdated(final File root) {
    myUpdatedExternals.add(root);
  }

  public boolean shouldRunFor(final File ioRoot) {
    boolean result = true;

    if (myUpdatedExternals.contains(ioRoot)) {
      result = false;
    }
    else if (FilePathUtil.isNested(myContentRoots, ioRoot)) {
      final RootUrlInfo info = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(ioRoot);

      if (info != null) {
        if (NestedCopyType.switched.equals(info.getType())) {
          result = false;
        }
        else if (NestedCopyType.external.equals(info.getType())) {
          result = !myVcs.getSvnConfiguration().isIgnoreExternals();
        }
      }
    }

    return result;
  }
}
