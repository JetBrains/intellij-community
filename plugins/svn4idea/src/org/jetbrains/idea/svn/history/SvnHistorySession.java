/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.List;

public class SvnHistorySession extends VcsAbstractHistorySession {
  private final SvnVcs myVcs;
  private final FilePath myCommittedPath;
  private final boolean myHaveMergeSources;
  private final boolean myHasLocalSource;

  public SvnHistorySession(SvnVcs vcs, final List<VcsFileRevision> revisions, final FilePath committedPath, final boolean haveMergeSources,
                           @Nullable final VcsRevisionNumber currentRevision, boolean skipRefreshOnStart, boolean source) {
    super(revisions, currentRevision);
    myVcs = vcs;
    myCommittedPath = committedPath;
    myHaveMergeSources = haveMergeSources;
    myHasLocalSource = source;
    if (!skipRefreshOnStart) {
      shouldBeRefreshed();
    }
  }

  public HistoryAsTreeProvider getHistoryAsTreeProvider() {
    return null;
  }

  @Nullable
  public VcsRevisionNumber calcCurrentRevisionNumber() {
    if (myCommittedPath == null) {
      return null;
    }
    if (myCommittedPath.isNonLocal()) {
      // technically, it does not make sense, since there's no "current" revision for non-local history (if look how it's used)
      // but ok, lets keep it for now
      return new SvnRevisionNumber(Revision.HEAD);
    }
    return getCurrentCommittedRevision(myVcs, new File(myCommittedPath.getPath()));
  }

  public static VcsRevisionNumber getCurrentCommittedRevision(final SvnVcs vcs, final File file) {
    Info info = vcs.getInfo(file);
    return info != null ? new SvnRevisionNumber(info.getCommittedRevision()) : null;
  }

  public FilePath getCommittedPath() {
    return myCommittedPath;
  }

  public boolean isHaveMergeSources() {
    return myHaveMergeSources;
  }

  @Override
  public VcsHistorySession copy() {
    return new SvnHistorySession(myVcs, getRevisionList(), myCommittedPath, myHaveMergeSources, getCurrentRevisionNumber(), true,
                                 myHasLocalSource);
  }

  @Override
  public boolean hasLocalSource() {
    return myHasLocalSource;
  }
}
