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
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Map;

public class FirstInBranch implements Runnable {
  private final SvnVcs myVcs;
  private final String myRepositoryRoot;
  private final String myFullBranchUrl;
  private final String myFullTrunkUrl;
  private final String myBranchUrl;
  private final String myTrunkUrl;
  private final Consumer<CopyData> myConsumer;
  private CopyData myResult;
  private final boolean myPrimary;

  public FirstInBranch(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl, final Consumer<CopyData> consumer) {
    this(vcs,  repositoryRoot, branchUrl, trunkUrl, consumer, true);
  }

  public FirstInBranch(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl, final Consumer<CopyData> consumer, final boolean primary) {
    myPrimary = primary;
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myConsumer = consumer;

    myResult = null;

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
    final SVNLogClient logClient = myVcs.createLogClient();

    try {
      logClient.doLog(SVNURL.parseURIEncoded(myFullBranchUrl), null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0), true, true, 0,
                      new ISVNLogEntryHandler() {
                        public void handleLogEntry(final SVNLogEntry logEntry) throws SVNException {
                          ProgressManager.checkCanceled();
                          
                          final Map map = logEntry.getChangedPaths();
                          for (Object o : map.values()) {
                            final SVNLogEntryPath path = (SVNLogEntryPath) o;
                            final String localPath = path.getPath();
                            final String copyPath = path.getCopyPath();

                            if ('A' == path.getType() &&
                                (myBranchUrl.equals(localPath) || SVNPathUtil.isAncestor(localPath, myBranchUrl)) &&
                                (myTrunkUrl.equals(copyPath)) || SVNPathUtil.isAncestor(copyPath, myTrunkUrl)) {
                              myResult = new CopyData(path.getCopyRevision(), logEntry.getRevision(), myPrimary);
                              throw new MockException();
                            }
                          }
                        }
                      });
    }
    catch (MockException e) {
      myConsumer.consume(myResult);
      return;
    }
    catch (SVNException e) {
      myConsumer.consume(myResult);
    }
    if (myPrimary) {
      new FirstInBranch(myVcs, myRepositoryRoot, myFullTrunkUrl, myFullBranchUrl, myConsumer, false).run();
    } else {
      myConsumer.consume(myResult);
    }
  }

  private static class MockException extends RuntimeException {}

}
