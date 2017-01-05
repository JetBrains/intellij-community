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

import com.google.common.base.MoreObjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.immutableList;
import static org.jetbrains.idea.svn.SvnUtil.*;
import static org.jetbrains.idea.svn.commandLine.CommandUtil.format;

public class FirstInBranch {

  private static final Logger LOG = Logger.getInstance(FirstInBranch.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final String myAbsoluteBranchUrl;
  @NotNull private final String myAbsoluteTrunkUrl;
  @NotNull private final SVNURL myRepositoryRoot;

  public FirstInBranch(@NotNull SvnVcs vcs, @NotNull SVNURL repositoryRoot, @NotNull String branchUrl, @NotNull String trunkUrl) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myAbsoluteBranchUrl = branchUrl;
    myAbsoluteTrunkUrl = trunkUrl;
  }

  @Nullable
  public CopyData run() throws VcsException {
    SvnTarget trunk = SvnTarget.fromURL(createUrl(myAbsoluteTrunkUrl), SVNRevision.HEAD);
    SvnTarget branch = SvnTarget.fromURL(createUrl(myAbsoluteBranchUrl), SVNRevision.HEAD);
    CopyData result = find(new BranchPoint(trunk), new BranchPoint(branch), true);

    debug(result);

    return result;
  }

  @Nullable
  private CopyData find(@NotNull BranchPoint trunk, @NotNull BranchPoint branch, boolean isBranchFromTrunk) throws VcsException {
    CopyData result = null;

    debug(trunk, branch, isBranchFromTrunk);

    if (trunk.hasCopyPath()) {
      if (StringUtil.equals(trunk.copyPath(), branch.relativePath())) {
        // trunk was copied from branch
        result = trunk.toCopyData(!isBranchFromTrunk);
      }
      else {
        if (branch.hasCopyPath()) {
          if (branch.copyRevision() == trunk.copyRevision()) {
            // both trunk and branch were copied from same point -> we assume parent branch is the one created earlier
            result = branch.revision() > trunk.revision() ? branch.toCopyData(isBranchFromTrunk) : trunk.toCopyData(!isBranchFromTrunk);
          }
          else {
            // both trunk and branch were copied from different points -> search recursively starting with latter revisions
            result = branch.copyRevision() > trunk.copyRevision()
                     ? find(trunk, new BranchPoint(branch.copyTarget()), isBranchFromTrunk)
                     : find(new BranchPoint(trunk.copyTarget()), branch, isBranchFromTrunk);
          }
        }
        else {
          // branch was not copied from anywhere -> search in trunk when it was copied from branch
          result = find(branch, trunk, !isBranchFromTrunk);
        }
      }
    }
    else if (branch.hasCopyPath()) {
      // trunk was not copied from anywhere -> search in branch when it was copied from trunk
      result = StringUtil.equals(branch.copyPath(), trunk.relativePath())
               ? branch.toCopyData(isBranchFromTrunk)
               : find(trunk, new BranchPoint(branch.copyTarget()), isBranchFromTrunk);
    }

    return result;
  }

  private void debug(@Nullable CopyData copyData) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Found branch point " + join(immutableList(myAbsoluteTrunkUrl, myAbsoluteBranchUrl, copyData), ", "));
    }
  }

  private void debug(@NotNull BranchPoint trunk, @NotNull BranchPoint branch, boolean isBranchFromTrunk) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Searching branch point for " + join(immutableList(trunk, branch, isBranchFromTrunk), ", "));
    }
  }

  private class BranchPoint {
    @NotNull private final SvnTarget myTarget;
    @Nullable private LogEntry myEntry;
    @Nullable private LogEntryPath myPath;

    private BranchPoint(@NotNull SvnTarget target) {
      myTarget = target;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
        .add("target", myTarget)
        .add("revision", myEntry != null ? myEntry.getRevision() : -1)
        .add("path", myPath != null && myPath.getCopyPath() != null
                     ? format(myPath.getCopyPath(), SVNRevision.create(myPath.getCopyRevision()))
                     : null)
        .toString();
    }

    private void init() throws VcsException {
      if (myEntry == null) {
        Pair<LogEntry, LogEntryPath> copyPoint = getCopyPoint();

        myEntry = copyPoint.first;
        myPath = copyPoint.second;
      }
    }

    @NotNull
    private Pair<LogEntry, LogEntryPath> getCopyPoint() throws VcsException {
      HistoryClient client = myVcs.getFactory(myTarget).createHistoryClient();
      Ref<LogEntry> entry = Ref.create();

      client.doLog(myTarget, SVNRevision.create(1), myTarget.getPegRevision(), true, true, false, 1, null, entry::set);

      if (entry.isNull()) {
        throw new VcsException("No branch point found for " + myTarget);
      }

      LogEntryPath path = entry.get().getChangedPaths().get(relativePath());

      if (path == null) {
        throw new VcsException(myTarget + " not found in " + entry.get().getChangedPaths());
      }

      return Pair.create(entry.get(), path);
    }

    private boolean hasCopyPath() throws VcsException {
      init();
      return notNull(myPath).getCopyPath() != null;
    }

    @NotNull
    private String copyPath() throws VcsException {
      init();
      return notNull(myPath).getCopyPath();
    }

    private long copyRevision() throws VcsException {
      init();
      return notNull(myPath).getCopyRevision();
    }

    @NotNull
    private SvnTarget copyTarget() throws VcsException {
      return SvnTarget.fromURL(append(myRepositoryRoot, copyPath()), SVNRevision.create(copyRevision()));
    }

    @NotNull
    private String relativePath() {
      return ensureStartSlash(getRelativeUrl(myRepositoryRoot.toDecodedString(), myTarget.getURL().toDecodedString()));
    }

    private long revision() throws VcsException {
      init();
      return notNull(myEntry).getRevision();
    }

    @NotNull
    private CopyData toCopyData(boolean isBranchFromTrunk) throws VcsException {
      return new CopyData(copyRevision(), revision(), isBranchFromTrunk);
    }
  }
}
