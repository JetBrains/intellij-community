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
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class FirstInBranch extends FirstInBranchAbstractBase {
  public FirstInBranch(SvnVcs vcs,
                        String repositoryRoot,
                        String branchUrl,
                        String trunkUrl,
                        Consumer<CopyData> consumer) {
    super(vcs, repositoryRoot, branchUrl, trunkUrl, consumer);
  }

  protected Consumer<Consumer<CopyData>> createTask(final SVNURL branchURL) {
    return new Consumer<Consumer<CopyData>>() {
      public void consume(final Consumer<CopyData> copyDataConsumer) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("FirstInBranch started for: " + branchURL.toString());
        }
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
            LOG.info(e);
          }
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("FirstInBranch finished for: " + branchURL.toString());
        }
      }
    };
  }

  private static long getStart(final SVNLogClient logClient, final SVNURL url) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("getting start revision for: " + url);
    }
    final Ref<Long> myRevisionCandidate = new Ref<Long>(0L);
    try {
      logClient.doLog(url, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0),
                      true, false, 0, new ISVNLogEntryHandler() {
          public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            ProgressManager.checkCanceled();

            myRevisionCandidate.set(logEntry.getRevision());
            if (LOG.isDebugEnabled()) {
              LOG.debug("setting in cycle start revision for: " + url + " as: " + myRevisionCandidate.get());
            }
          }
        });
    }
    catch (SVNException e) {
      LOG.info(e);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("start revision for: " + url + " is: " + myRevisionCandidate.get());
    }
    return myRevisionCandidate.get();
  }
}
