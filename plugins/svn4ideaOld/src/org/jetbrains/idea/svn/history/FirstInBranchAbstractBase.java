/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.ConcurrentTasks;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/15/11
 * Time: 1:30 PM
 */
public abstract class FirstInBranchAbstractBase implements Runnable {
  protected final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.FirstInBranch");
  protected final SvnVcs myVcs;
  protected final String myFullBranchUrl;
  protected final String myFullTrunkUrl;
  protected final String myBranchUrl;
  protected final String myTrunkUrl;
  protected final Consumer<CopyData> myConsumer;

  public FirstInBranchAbstractBase(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl,
                                   final Consumer<CopyData> consumer) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("FirstInBranchAbstractBase created with: repoRoot: " + repositoryRoot + " branchUrl: " + branchUrl +
              " trunkUrl: " + trunkUrl);
    }
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
      LOG.info(e);
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

  protected abstract Consumer<Consumer<CopyData>> createTask(final SVNURL branchURL);

  protected void checkForCopy(final SVNLogEntry logEntry, final Consumer<CopyData> result) {
    final Map map = logEntry.getChangedPaths();
    for (Object o : map.values()) {
      final SVNLogEntryPath path = (SVNLogEntryPath) o;
      final String localPath = path.getPath();
      final String copyPath = path.getCopyPath();
      if (LOG.isDebugEnabled()) {
        LOG.debug("localPath: " + localPath + " copy path: " + copyPath + " revision: " + logEntry.getRevision());
      }

      if ('A' == path.getType()) {
        if ((myBranchUrl.equals(localPath) || SVNPathUtil.isAncestor(localPath, myBranchUrl)) &&
            ((myTrunkUrl.equals(copyPath)) || SVNPathUtil.isAncestor(copyPath, myTrunkUrl))) {
          result.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), true));
        } else {
          if ((myBranchUrl.equals(copyPath) || SVNPathUtil.isAncestor(copyPath, myBranchUrl)) &&
              ((myTrunkUrl.equals(localPath)) || SVNPathUtil.isAncestor(localPath, myTrunkUrl))) {
            result.consume(new CopyData(path.getCopyRevision(), logEntry.getRevision(), false));
          }
        }
      }
    }
  }
}
