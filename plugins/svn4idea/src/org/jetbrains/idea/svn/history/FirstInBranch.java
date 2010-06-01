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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ConcurrentTasks;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Map;

public class FirstInBranch implements Runnable {
  private final SvnVcs myVcs;
  private final String myFullBranchUrl;
  private final String myFullTrunkUrl;
  private final String myBranchUrl;
  private final String myTrunkUrl;
  private final Consumer<CopyData> myConsumer;

  public FirstInBranch(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl, final Consumer<CopyData> consumer) {
    myVcs = vcs;
    myConsumer = consumer;

    myFullBranchUrl = branchUrl;
    myFullTrunkUrl = trunkUrl;
    myBranchUrl = relativePath(repositoryRoot, branchUrl);
    myTrunkUrl = relativePath(repositoryRoot, trunkUrl);
  }

  private String relativePath(final String parent, final String child) {
    String path = SVNPathUtil.getRelativePath(parent, child);
    return path.startsWith("/") ? path : "/" + path;
  }

  public void run() {
    final SVNURL branchURL;
    final SVNURL trunkURL;
    try {
      branchURL = SVNURL.parseURIEncoded(myFullBranchUrl);
      trunkURL = SVNURL.parseURIEncoded(myFullTrunkUrl);
    }
    catch (SVNException e) {
      myConsumer.consume(null);
      return;
    }

    final ConcurrentTasks<CopyData> tasks =
      new ConcurrentTasks<CopyData>(ProgressManager.getInstance().getProgressIndicator(), createTask(branchURL), createTask(trunkURL));
    tasks.compute();
    if (tasks.isResultKnown()) {
      myConsumer.consume(tasks.getResult());
    } else {
      myConsumer.consume(null);
    }
  }

  private Consumer<Consumer<CopyData>> createTask(final SVNURL branchURL) {
    return new Consumer<Consumer<CopyData>>() {
      public void consume(final Consumer<CopyData> copyDataConsumer) {
        final SVNLogClient logClient = myVcs.createLogClient();
        final long start1 = getStart(logClient, branchURL);
        if (start1 > 0) {
          final SVNRevision start1Rev = SVNRevision.create(start1);
          try {
            logClient.doLog(branchURL, null, SVNRevision.UNDEFINED, start1Rev, start1Rev, true, true, 1, new ISVNLogEntryHandler() {
              public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                checkForCopy(logEntry, copyDataConsumer);
              }
            });
          } catch (SVNException e) {
            //
          }
        }
      }
    };
  }

  private static long getStart(final SVNLogClient logClient, final SVNURL url) {
    final Ref<Long> myRevisionCandidate = new Ref<Long>(0L);
    try {
      logClient.doLog(url, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0),
                      true, false, 0, new ISVNLogEntryHandler() {
          public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            ProgressManager.checkCanceled();

            myRevisionCandidate.set(logEntry.getRevision());
          }
        });
    }
    catch (SVNException e) {
      //
    }
    return myRevisionCandidate.get();
  }

  private void checkForCopy(final SVNLogEntry logEntry, final Consumer<CopyData> result) {
    final Map map = logEntry.getChangedPaths();
    for (Object o : map.values()) {
      final SVNLogEntryPath path = (SVNLogEntryPath) o;
      final String localPath = path.getPath();
      final String copyPath = path.getCopyPath();

      if ('A' == path.getType()) {
        if ((myBranchUrl.equals(localPath) || SVNPathUtil.isAncestor(localPath, myBranchUrl)) &&
            (myTrunkUrl.equals(copyPath)) || SVNPathUtil.isAncestor(copyPath, myTrunkUrl)) {
          result.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), true));
        } else {
          if ((myBranchUrl.equals(copyPath) || SVNPathUtil.isAncestor(copyPath, myBranchUrl)) &&
              (myTrunkUrl.equals(localPath)) || SVNPathUtil.isAncestor(localPath, myTrunkUrl)) {
            result.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), false));
          }
        }
      }
    }
  }
}
