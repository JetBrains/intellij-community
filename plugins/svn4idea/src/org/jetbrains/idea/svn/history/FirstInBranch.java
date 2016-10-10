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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.isAncestor;

// TODO: This one seem to determine revision in which branch was created - copied from trunk.
// TODO: This could be done in one command "svn log <folder> -r 0:HEAD --stop-on-copy --limit 1".
// TODO: Check for 1.7 and rewrite using this approach.
public class FirstInBranch {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final String myRepositoryRelativeBranchUrl;
  @NotNull private final String myRepositoryRelativeTrunkUrl;
  @NotNull private final SVNURL myRepositoryRoot;

  public FirstInBranch(@NotNull SvnVcs vcs, @NotNull SVNURL repositoryRoot, @NotNull String branchUrl, @NotNull String trunkUrl) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myRepositoryRelativeBranchUrl = ensureStartSlash(getRelativeUrl(repositoryRoot.toDecodedString(), branchUrl));
    myRepositoryRelativeTrunkUrl = ensureStartSlash(getRelativeUrl(repositoryRoot.toDecodedString(), trunkUrl));
  }

  @Nullable
  public CopyData run() throws VcsException {
    SvnTarget target = SvnTarget.fromURL(myRepositoryRoot);
    HistoryClient client = ApplicationManager.getApplication().runReadAction((Computable<HistoryClient>)() -> {
      if (myVcs.getProject().isDisposed()) return null;
      return myVcs.getFactory(target).createHistoryClient();
    });

    if (client == null) return null;

    MyLogEntryHandler handler = new MyLogEntryHandler(myRepositoryRelativeTrunkUrl, myRepositoryRelativeBranchUrl);

    try {
      client.doLog(target, SVNRevision.HEAD, SVNRevision.create(0), false, true, false, -1, null, handler);
    }
    catch (SvnBindException e) {
      // do not throw cancel exception as this means corresponding copy point is found (if progress indicator was not explicitly cancelled)
      if (!e.contains(SVNErrorCode.CANCELLED)) {
        throw e;
      }
    }

    return handler.getCopyData();
  }

  private static class MyLogEntryHandler implements LogEntryConsumer {

    @NotNull private final SvnPathThroughHistoryCorrection myTrunkCorrector;
    @NotNull private final SvnPathThroughHistoryCorrection myBranchCorrector;
    @Nullable private CopyData myCopyData;

    public MyLogEntryHandler(@NotNull String repositoryRelativeTrunkUrl, @NotNull String repositoryRelativeBranchUrl) {
      myTrunkCorrector = new SvnPathThroughHistoryCorrection(repositoryRelativeTrunkUrl);
      myBranchCorrector = new SvnPathThroughHistoryCorrection(repositoryRelativeBranchUrl);
    }

    @Nullable
    public CopyData getCopyData() {
      return myCopyData;
    }

    @Override
    public void consume(@NotNull LogEntry logEntry) throws SVNException {
      checkEntries(logEntry);
      myTrunkCorrector.consume(logEntry);
      myBranchCorrector.consume(logEntry);
      checkEntries(logEntry);
    }

    private void checkEntries(@NotNull LogEntry logEntry) throws SVNCancelException {
      for (LogEntryPath path : logEntry.getChangedPaths().values()) {
        if ('A' == path.getType() && checkForCopyCase(logEntry, path)) {
          throw new SVNCancelException();
        }
      }
    }

    private boolean checkForCopyCase(@NotNull LogEntry logEntry, @NotNull LogEntryPath path) {
      String trunkUrl = myTrunkCorrector.getCurrentPath();
      String branchUrl = myBranchCorrector.getCurrentPath();
      boolean isBranchCopiedFromTrunk = equalOrParent(path.getPath(), branchUrl) && equalOrParent(path.getCopyPath(), trunkUrl);
      boolean isTrunkCopiedFromBranch = equalOrParent(path.getPath(), trunkUrl) && equalOrParent(path.getCopyPath(), branchUrl);

      if (isBranchCopiedFromTrunk || isTrunkCopiedFromBranch) {
        myCopyData = new CopyData(path.getCopyRevision(), logEntry.getRevision(), isBranchCopiedFromTrunk);
      }

      return myCopyData != null;
    }

    private static boolean equalOrParent(@Nullable String parentCandidate, @NotNull String childCandidate) {
      return childCandidate.equals(parentCandidate) || isAncestor(parentCandidate, childCandidate);
    }
  }
}
