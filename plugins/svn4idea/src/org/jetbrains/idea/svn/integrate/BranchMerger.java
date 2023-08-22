// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class BranchMerger implements IMerger {

  private static final Logger LOG = Logger.getInstance(BranchMerger.class);

  private final SvnVcs myVcs;
  private final String myTargetPath;
  private final Url mySourceUrl;
  private final UpdateEventHandler myHandler;
  private final boolean myReintegrate;
  private final String myBranchName;
  private final long mySourceCopyRevision;
  private boolean myAtStart;
  private Revision mySourceLatestRevision;
  private final boolean mySupportsMergeInfo;

  public BranchMerger(final SvnVcs vcs,
                      final Url sourceUrl,
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

  @Override
  public @NotNull String getComment() {
    return mySupportsMergeInfo
           ? myReintegrate
             ? message("label.merge.all.from.branch.reintegrate", myBranchName)
             : message("label.merge.all.from.branch", myBranchName)
           : myReintegrate
             ? message("label.merge.all.from.branch.at.revision.reintegrate", myBranchName, mySourceLatestRevision)
             : message("label.merge.all.from.branch.at.revision", myBranchName, mySourceLatestRevision);
  }

  @Override
  public boolean hasNext() {
    return myAtStart;
  }

  @Override
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

  @Override
  @Nullable
  public String getInfo() {
    return null;
  }

  @Override
  public File getMergeInfoHolder() {
    return new File(myTargetPath);
  }

  @Override
  public void afterProcessing() {
  }

  @Override
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
