// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChangeSetMergerFactory implements MergerFactory {

  @NotNull private final CommittedChangeList myChangeList;
  @NotNull private final List<Change> myChanges;

  public ChangeSetMergerFactory(@NotNull CommittedChangeList changeList, @NotNull Collection<Change> changes) {
    myChangeList = changeList;
    myChanges = new ArrayList<>(changes);
  }

  @Override
  public IMerger createMerger(final SvnVcs vcs,
                              final File target,
                              final UpdateEventHandler handler,
                              final Url currentBranchUrl,
                              String branchName) {
    return new PointMerger(vcs, myChangeList, target, handler, currentBranchUrl, myChanges, branchName);
  }
}
