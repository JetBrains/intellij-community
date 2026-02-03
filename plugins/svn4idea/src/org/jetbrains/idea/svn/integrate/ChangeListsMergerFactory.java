// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChangeListsMergerFactory implements MergerFactory {

  protected final @NotNull List<CommittedChangeList> myChangeLists;
  private final boolean myRecordOnly;
  private final boolean myInvertRange;
  private final boolean myGroupSequentialChangeLists;

  public ChangeListsMergerFactory(@NotNull List<? extends CommittedChangeList> changeLists,
                                  boolean recordOnly,
                                  boolean invertRange,
                                  boolean groupSequentialChangeLists) {
    myChangeLists = new ArrayList<>(changeLists);
    myRecordOnly = recordOnly;
    myInvertRange = invertRange;
    myGroupSequentialChangeLists = groupSequentialChangeLists;
  }

  @Override
  public IMerger createMerger(final SvnVcs vcs,
                              final File target,
                              final UpdateEventHandler handler,
                              final Url currentBranchUrl,
                              String branchName) {
    return new Merger(vcs, myChangeLists, target, handler, currentBranchUrl, branchName, myRecordOnly, myInvertRange,
                      myGroupSequentialChangeLists);
  }
}
