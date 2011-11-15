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

import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/15/11
 * Time: 1:23 PM
 */
public class FirstInBranchAccurate extends FirstInBranchAbstractBase {
  public FirstInBranchAccurate(SvnVcs vcs,
                                String repositoryRoot,
                                String branchUrl,
                                String trunkUrl,
                                Consumer<CopyData> consumer) {
    super(vcs, repositoryRoot, branchUrl, trunkUrl, consumer);
  }

  @Override
  protected Consumer<Consumer<CopyData>> createTask(final SVNURL branchURL) {
    return new Consumer<Consumer<CopyData>>() {
      public void consume(final Consumer<CopyData> copyDataConsumer) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("FirstInBranchAccurate started for: " + branchURL.toString());
        }
        final SVNLogClient logClient = myVcs.createLogClient();
        try {
          logClient.doLog(branchURL, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0), true, true, 1, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
              checkForCopy(logEntry, copyDataConsumer);
            }
          });
        } catch (SVNException e) {
          LOG.info(e);
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("FirstInBranchAccurate finished for: " + branchURL.toString());
        }
      }
    };
  }
}
