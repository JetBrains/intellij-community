/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.util.NotNullFunction;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.IMerger;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

public class BranchMerger implements IMerger {
  private final SvnVcs myVcs;
  private final String myTargetPath;
  private final SVNURL mySourceUrl;
  private final SVNURL myTargetUrl;
  private final UpdateEventHandler myHandler;
  private final boolean myReintegrate;
  private final String myBranchName;
  private final long mySourceCopyRevision;
  private boolean myAtStart;
  private long mySourceLatestRevision;

  public BranchMerger(final SvnVcs vcs,
                      final SVNURL sourceUrl,
                      final SVNURL targetUrl, final String targetPath,
                      final UpdateEventHandler handler,
                      final boolean isReintegrate, final String branchName, final long sourceCopyRevision) {
    myVcs = vcs;
    myTargetPath = targetPath;
    mySourceUrl = sourceUrl;
    myTargetUrl = targetUrl;
    myHandler = handler;
    myReintegrate = isReintegrate;
    myBranchName = branchName;
    mySourceCopyRevision = sourceCopyRevision;
    myAtStart = true;
    try {
      mySourceLatestRevision = myVcs.createRepository(mySourceUrl).getLatestRevision();
    }
    catch (SVNException e) {
      mySourceLatestRevision = SVNRevision.HEAD.getNumber();
    }
  }

  public String getComment() {
    return "Merge all from " + myBranchName + " at " + mySourceLatestRevision +(myReintegrate ? " (reintegration)" : "");
  }

  public boolean hasNext() {
    return myAtStart;
  }

  public void mergeNext() throws SVNException {
    myAtStart = false;
    final SVNDiffClient dc = myVcs.createDiffClient();
    dc.setEventHandler(myHandler);
    final SvnConfiguration svnConfig = SvnConfiguration.getInstanceChecked(myVcs.getProject());
    dc.setMergeOptions(new SVNDiffOptions(svnConfig.IGNORE_SPACES_IN_MERGE, svnConfig.IGNORE_SPACES_IN_MERGE,
                                                    svnConfig.IGNORE_SPACES_IN_MERGE));

    if (myReintegrate) {
      dc.doMergeReIntegrate(mySourceUrl, SVNRevision.UNDEFINED, new File(myTargetPath), false);
    } else {
      dc.doMerge(mySourceUrl, SVNRevision.create(mySourceCopyRevision), mySourceUrl, SVNRevision.create(mySourceLatestRevision),
        new File(myTargetPath), SVNDepth.INFINITY, true, true, false, false);
    }
  }

  public void getInfo(NotNullFunction<String, Boolean> holder, boolean getLatest) {
  }

  public File getMergeInfoHolder() {
    return new File(myTargetPath);
  }

  public void afterProcessing() {
  }
}
