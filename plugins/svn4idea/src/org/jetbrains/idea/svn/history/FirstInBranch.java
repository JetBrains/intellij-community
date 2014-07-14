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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.Map;

// TODO: This one seem to determine revision in which branch was created - copied from trunk.
// TODO: This could be done in one command "svn log <folder> -r 0:HEAD --stop-on-copy --limit 1".
// TODO: Check for 1.7 and rewrite using this approach.
public class FirstInBranch implements Runnable {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.FirstInBranch");

  @NotNull private final SvnVcs myVcs;
  @NotNull private final String myBranchUrl;
  @NotNull private final String myTrunkUrl;
  @NotNull private final String myRepositoryRoot;
  @NotNull private final TransparentlyFailedValueI<CopyData, VcsException> myConsumer;

  public FirstInBranch(@NotNull SvnVcs vcs, @NotNull String repositoryRoot, @NotNull String branchUrl, @NotNull String trunkUrl,
                       @NotNull TransparentlyFailedValueI<CopyData, VcsException> consumer) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myConsumer = consumer;

    myBranchUrl = relativePath(repositoryRoot, branchUrl);
    myTrunkUrl = relativePath(repositoryRoot, trunkUrl);
  }

  private static String relativePath(final String parent, final String child) {
    String path = SVNPathUtil.getRelativePath(parent, child);

    return SvnUtil.ensureStartSlash(path);
  }

  private SVNURL getRepositoryRootUrl() {
    SVNURL result = null;

    try {
      result = SvnUtil.createUrl(myRepositoryRoot);
    }
    catch (SvnBindException e) {
      LOG.info(e);
      myConsumer.fail(e);
    }

    return result;
  }

  public void run() {
    SVNURL repositoryRootUrl = getRepositoryRootUrl();

    if (repositoryRootUrl != null) {
      run(repositoryRootUrl);
    }
  }

  private void run(@NotNull SVNURL url) {
    try {
      run(url, new Consumer<CopyData>() {
        @Override
        public void consume(CopyData data) {
          if (data != null) {
            myConsumer.set(data);
          }
        }
      });
    }
    catch (VcsException e) {
      LOG.info(e);
      myConsumer.fail(e);
    }
  }

  private void run(@NotNull SVNURL branchURL, @NotNull Consumer<CopyData> copyDataConsumer) throws VcsException {
    final SvnTarget target = SvnTarget.fromURL(branchURL);

    HistoryClient client = ApplicationManager.getApplication().runReadAction(new Computable<HistoryClient>() {
      @Override
      public HistoryClient compute() {
        if (myVcs.getProject().isDisposed()) return null;
        return myVcs.getFactory(target).createHistoryClient();
      }
    });

    if (client == null) return;

    try {
      client.doLog(target, SVNRevision.HEAD, SVNRevision.create(0), false, true, false, -1, null,
                   new MyLogEntryHandler(copyDataConsumer, myTrunkUrl, myBranchUrl));
    }
    catch (SvnBindException e) {
      // do not throw cancel exception as this means corresponding copy point is found (if progress indicator was not explicitly cancelled)
      if (!e.contains(SVNErrorCode.CANCELLED)) {
        throw e;
      }
    }
  }

  private static class MyLogEntryHandler implements LogEntryConsumer {

    @NotNull private final SvnPathThroughHistoryCorrection myTrunkCorrector;
    @NotNull private final SvnPathThroughHistoryCorrection myBranchCorrector;
    @NotNull private final Consumer<CopyData> myCopyDataConsumer;

    public MyLogEntryHandler(@NotNull Consumer<CopyData> copyDataConsumer, @NotNull String trunkUrl, @NotNull String branchUrl) {
      myCopyDataConsumer = copyDataConsumer;
      myTrunkCorrector = new SvnPathThroughHistoryCorrection(trunkUrl);
      myBranchCorrector = new SvnPathThroughHistoryCorrection(branchUrl);
    }

    @Override
    public void consume(LogEntry logEntry) throws SVNException {
      final Map map = logEntry.getChangedPaths();
      checkEntries(logEntry, map);
      myTrunkCorrector.consume(logEntry);
      myBranchCorrector.consume(logEntry);
      checkEntries(logEntry, map);
    }

    private void checkEntries(LogEntry logEntry, Map map) throws SVNCancelException {
      for (Object o : map.values()) {
        final LogEntryPath path = (LogEntryPath) o;
        final String localPath = path.getPath();
        final String copyPath = path.getCopyPath();

        if ('A' == path.getType()) {
          if (checkForCopyCase(logEntry, path, localPath, copyPath, myTrunkCorrector.getCurrentPath(), myBranchCorrector.getCurrentPath())) {
            throw new SVNCancelException();
          }
        }
      }
    }

    private boolean checkForCopyCase(LogEntry logEntry, LogEntryPath path, String localPath, String copyPath,
                                     final String trunkUrl, final String branchUrl) {
      if (equalOrParent(localPath, branchUrl) && equalOrParent(copyPath, trunkUrl)) {
        myCopyDataConsumer.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), true));
        return true;
      } else {
        if ((equalOrParent(copyPath, branchUrl)) && equalOrParent(localPath, trunkUrl)) {
          myCopyDataConsumer.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), false));
          return true;
        }
      }
      return false;
    }

    private static boolean equalOrParent(String localPath, final String targetPath) {
      return targetPath.equals(localPath) || SVNPathUtil.isAncestor(localPath, targetPath);
    }
  }
}
