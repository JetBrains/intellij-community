/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

public class ChangeListsMergerFactory implements MergerFactory {

  @NotNull protected final List<CommittedChangeList> myChangeLists;
  private final boolean myRecordOnly;
  private final boolean myInvertRange;
  private final boolean myGroupSequentialChangeLists;

  public ChangeListsMergerFactory(@NotNull List<CommittedChangeList> changeLists,
                                  boolean recordOnly,
                                  boolean invertRange,
                                  boolean groupSequentialChangeLists) {
    myChangeLists = ContainerUtil.newArrayList(changeLists);
    myRecordOnly = recordOnly;
    myInvertRange = invertRange;
    myGroupSequentialChangeLists = groupSequentialChangeLists;
  }

  @Override
  public IMerger createMerger(final SvnVcs vcs,
                              final File target,
                              final UpdateEventHandler handler,
                              final SVNURL currentBranchUrl,
                              String branchName) {
    return new Merger(vcs, myChangeLists, target, handler, currentBranchUrl, branchName, myRecordOnly, myInvertRange,
                      myGroupSequentialChangeLists);
  }
}
