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
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.Consumer;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Map;
import java.util.Set;

public class FirstInBranch implements Runnable {
  private final SvnVcs myVcs;
  private final String myBranchUrl;
  private final String myTrunkUrl;
  private final String myRepositoryRoot;
  private final TransparentlyFailedValueI<CopyData, SVNException> myConsumer;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.FirstInBranch");

  public FirstInBranch(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl,
                       final TransparentlyFailedValueI<CopyData, SVNException> consumer) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myConsumer = consumer;

    myBranchUrl = relativePath(repositoryRoot, branchUrl);
    myTrunkUrl = relativePath(repositoryRoot, trunkUrl);
  }

  private String relativePath(final String parent, final String child) {
    String path = SVNPathUtil.getRelativePath(parent, child);
    return path.startsWith("/") ? path : "/" + path;
  }

  public void run() {
    final Set<SVNException> exceptions = new HashSet<SVNException>();
    final boolean [] called = new boolean[1];
    try {
      createTask(SVNURL.parseURIDecoded(myRepositoryRoot), exceptions).consume(new Consumer<CopyData>() {
        @Override
        public void consume(CopyData data) {
          if (data != null) {
            myConsumer.set(data);
            called[0] = true;
          }
        }
      });
    }
    catch (SVNException e) {
      myConsumer.fail(e);
      return;
    }
    if (called[0]) return;

    if (! exceptions.isEmpty()) {
      LOG.info("Wasn't able to find branch point, exception(s) below");
      for (SVNException exception : exceptions) {
        LOG.info(exception);
      }
      myConsumer.fail(exceptions.iterator().next());
    } else if (! called[0]) {
      myConsumer.set(null);
    }
  }

  private Consumer<Consumer<CopyData>> createTask(final SVNURL branchURL, final Set<SVNException> exceptions) {
    return new Consumer<Consumer<CopyData>>() {
      public void consume(final Consumer<CopyData> copyDataConsumer) {
        final SVNLogClient logClient = ApplicationManager.getApplication().runReadAction(new Computable<SVNLogClient>() {
          @Override
          public SVNLogClient compute() {
            if (myVcs.getProject().isDisposed()) return null;
            return myVcs.createLogClient();
          }
        });
        if (logClient == null) return;
        try {
            logClient.doLog(branchURL, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0), false, true, -1,
                            new MyLogEntryHandler(copyDataConsumer, myTrunkUrl, myBranchUrl));
        } catch (SVNCancelException e) {
          //
        } catch (SVNException e) {
          exceptions.add(e);
        }
      }
    };
  }

  private static class MyLogEntryHandler implements ISVNLogEntryHandler {
    private final SvnPathThroughHistoryCorrection myTrunkCorrector;
    private final SvnPathThroughHistoryCorrection myBranchCorrector;
    private final Consumer<CopyData> myCopyDataConsumer;

    public MyLogEntryHandler(Consumer<CopyData> copyDataConsumer, String trunkUrl, String branchUrl) {
      myCopyDataConsumer = copyDataConsumer;
      myTrunkCorrector = new SvnPathThroughHistoryCorrection(trunkUrl);
      myBranchCorrector = new SvnPathThroughHistoryCorrection(branchUrl);
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
      final Map map = logEntry.getChangedPaths();
      checkEntries(logEntry, map);
      myTrunkCorrector.handleLogEntry(logEntry);
      myBranchCorrector.handleLogEntry(logEntry);
      checkEntries(logEntry, map);
    }

    private void checkEntries(SVNLogEntry logEntry, Map map) throws SVNCancelException {
      for (Object o : map.values()) {
        final SVNLogEntryPath path = (SVNLogEntryPath) o;
        final String localPath = path.getPath();
        final String copyPath = path.getCopyPath();

        if ('A' == path.getType()) {
          if (checkForCopyCase(logEntry, path, localPath, copyPath, myTrunkCorrector.getCurrentPath(), myBranchCorrector.getCurrentPath())) {
            throw new SVNCancelException();
          }
        }
      }
    }

    private boolean checkForCopyCase(SVNLogEntry logEntry, SVNLogEntryPath path, String localPath, String copyPath,
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
