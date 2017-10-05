/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.RevisionRange;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

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
  private Revision mySourceLatestRevision;
  private final boolean mySupportsMergeInfo;

  public BranchMerger(final SvnVcs vcs,
                      final SVNURL sourceUrl,
                      final String targetPath,
                      final UpdateEventHandler handler,
                      final boolean isReintegrate,
                      final String branchName,
                      long sourceCopyRevision,
                      boolean supportsMergeInfo) {
    myVcs = vcs;
    myTargetPath = targetPath;
    mySourceUrl = sourceUrl;
    myHandler = handler;
    myReintegrate = isReintegrate;
    myBranchName = branchName;
    mySourceCopyRevision = sourceCopyRevision;
    myAtStart = true;
    mySupportsMergeInfo = supportsMergeInfo;
  }

  public String getComment() {
    return "Merge all from " + myBranchName +
           (!mySupportsMergeInfo ? " at " + mySourceLatestRevision : "") +
           (myReintegrate ? " (reintegration)" : "");
  }

  public boolean hasNext() {
    return myAtStart;
  }

  public void mergeNext() throws VcsException {
    myAtStart = false;

    File destination = new File(myTargetPath);
    MergeClient client = myVcs.getFactory(destination).createMergeClient();
    Target source = Target.on(mySourceUrl);

    if (mySupportsMergeInfo) {
      client.merge(source, destination, false, myReintegrate, createDiffOptions(), myHandler);
    } else {
      mySourceLatestRevision = resolveSourceLatestRevision();
      RevisionRange range = new RevisionRange(Revision.of(mySourceCopyRevision), mySourceLatestRevision);

      client.merge(source, range, destination, Depth.UNKNOWN, false, false, true, createDiffOptions(), myHandler);
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
  public Revision resolveSourceLatestRevision() {
    Revision result = Revision.HEAD;

    try {
      result = SvnUtil.getHeadRevision(myVcs, mySourceUrl);
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }

    return result;
  }
}
