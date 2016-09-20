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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
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

import java.util.Map;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.getRelativePath;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.isAncestor;

// TODO: This one seem to determine revision in which branch was created - copied from trunk.
// TODO: This could be done in one command "svn log <folder> -r 0:HEAD --stop-on-copy --limit 1".
// TODO: Check for 1.7 and rewrite using this approach.
public class FirstInBranch {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final String myBranchUrl;
  @NotNull private final String myTrunkUrl;
  @NotNull private final String myRepositoryRoot;

  public FirstInBranch(@NotNull SvnVcs vcs, @NotNull String repositoryRoot, @NotNull String branchUrl, @NotNull String trunkUrl) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myBranchUrl = relativePath(repositoryRoot, branchUrl);
    myTrunkUrl = relativePath(repositoryRoot, trunkUrl);
  }

  @NotNull
  private static String relativePath(@NotNull String parent, @NotNull String child) {
    return ensureStartSlash(getRelativePath(parent, child));
  }

  @Nullable
  public CopyData run() throws VcsException {
    Ref<CopyData> result = Ref.create();
    run(createUrl(myRepositoryRoot), data -> result.set(data));
    return result.get();
  }

  private void run(@NotNull SVNURL branchURL, @NotNull Consumer<CopyData> copyDataConsumer) throws VcsException {
    SvnTarget target = SvnTarget.fromURL(branchURL);
    HistoryClient client = ApplicationManager.getApplication().runReadAction((Computable<HistoryClient>)() -> {
      if (myVcs.getProject().isDisposed()) return null;
      return myVcs.getFactory(target).createHistoryClient();
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
    public void consume(@NotNull LogEntry logEntry) throws SVNException {
      Map map = logEntry.getChangedPaths();
      checkEntries(logEntry, map);
      myTrunkCorrector.consume(logEntry);
      myBranchCorrector.consume(logEntry);
      checkEntries(logEntry, map);
    }

    private void checkEntries(@NotNull LogEntry logEntry, @NotNull Map map) throws SVNCancelException {
      for (Object o : map.values()) {
        LogEntryPath path = (LogEntryPath)o;
        String localPath = path.getPath();
        String copyPath = path.getCopyPath();

        if ('A' == path.getType() &&
            checkForCopyCase(logEntry, path, localPath, copyPath, myTrunkCorrector.getCurrentPath(), myBranchCorrector.getCurrentPath())) {
          throw new SVNCancelException();
        }
      }
    }

    private boolean checkForCopyCase(@NotNull LogEntry logEntry,
                                     @NotNull LogEntryPath path,
                                     String localPath,
                                     String copyPath,
                                     String trunkUrl,
                                     String branchUrl) {
      if (equalOrParent(localPath, branchUrl) && equalOrParent(copyPath, trunkUrl)) {
        myCopyDataConsumer.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), true));
        return true;
      }
      else if (equalOrParent(copyPath, branchUrl) && equalOrParent(localPath, trunkUrl)) {
        myCopyDataConsumer.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), false));
        return true;
      }
      return false;
    }

    private static boolean equalOrParent(String localPath, String targetPath) {
      return targetPath.equals(localPath) || isAncestor(localPath, targetPath);
    }
  }
}
