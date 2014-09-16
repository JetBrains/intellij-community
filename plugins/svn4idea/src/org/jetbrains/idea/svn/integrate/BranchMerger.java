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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class BranchMerger implements IMerger {

  private static final Logger LOG = Logger.getInstance(BranchMerger.class);

  private final SvnVcs myVcs;
  private final String myTargetPath;
  private final SVNURL mySourceUrl;
  private final UpdateEventHandler myHandler;
  private final boolean myReintegrate;
  private final String myBranchName;
  private final long mySourceCopyRevision;
  private boolean myAtStart;
  private SVNRevision mySourceLatestRevision;

  public BranchMerger(final SvnVcs vcs,
                      final SVNURL sourceUrl,
                      final String targetPath,
                      final UpdateEventHandler handler,
                      final boolean isReintegrate, final String branchName, final long sourceCopyRevision) {
    myVcs = vcs;
    myTargetPath = targetPath;
    mySourceUrl = sourceUrl;
    myHandler = handler;
    myReintegrate = isReintegrate;
    myBranchName = branchName;
    mySourceCopyRevision = sourceCopyRevision;
    myAtStart = true;
    mySourceLatestRevision = resolveSourceLatestRevision();
  }

  public String getComment() {
    return "Merge all from " + myBranchName + " at " + mySourceLatestRevision +(myReintegrate ? " (reintegration)" : "");
  }

  public boolean hasNext() {
    return myAtStart;
  }

  public void mergeNext() throws VcsException {
    myAtStart = false;

    File destination = new File(myTargetPath);
    MergeClient client = myVcs.getFactory(destination).createMergeClient();

    if (myReintegrate) {
      client.merge(SvnTarget.fromURL(mySourceUrl), destination, false, createDiffOptions(), myHandler);
    } else {
      client.merge(SvnTarget.fromURL(mySourceUrl, SVNRevision.create(mySourceCopyRevision)),
                   SvnTarget.fromURL(mySourceUrl, mySourceLatestRevision), destination, Depth.INFINITY, true, false, false, true,
                   createDiffOptions(), myHandler);
    }
  }

  @NotNull
  private DiffOptions createDiffOptions() {
    return myVcs.getSvnConfiguration().getMergeOptions();
  }

  @Nullable
  public String getInfo() {
    return null;
  }

  public File getMergeInfoHolder() {
    return new File(myTargetPath);
  }

  public void afterProcessing() {
  }

  @Nullable
  public String getSkipped() {
    return null;
  }

  @NotNull
  public SVNRevision resolveSourceLatestRevision() {
    SVNRevision result = SVNRevision.HEAD;

    try {
      result = SvnUtil.getHeadRevision(myVcs, mySourceUrl);
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }

    return result;
  }
}
