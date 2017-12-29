// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

public class LookForBranchOriginTask extends BaseMergeTask {

  private final boolean myFromSource;
  @NotNull private final Consumer<SvnBranchPointsCalculator.WrapperInvertor> myCallback;

  public LookForBranchOriginTask(@NotNull QuickMerge mergeProcess,
                                 boolean fromSource,
                                 @NotNull Consumer<SvnBranchPointsCalculator.WrapperInvertor> callback) {
    super(mergeProcess);
    myFromSource = fromSource;
    myCallback = callback;
  }

  @Override
  public void run() throws VcsException {
    Url repoUrl = myMergeContext.getWcInfo().getRootInfo().getRepositoryUrl();
    Url sourceUrl = myFromSource ? myMergeContext.getSourceUrl() : myMergeContext.getWcInfo().getUrl();
    Url targetUrl = myFromSource ? myMergeContext.getWcInfo().getUrl() : myMergeContext.getSourceUrl();
    SvnBranchPointsCalculator.WrapperInvertor copyPoint =
      myMergeContext.getVcs().getSvnBranchPointsCalculator().calculateCopyPoint(repoUrl, sourceUrl.toString(), targetUrl.toString());

    if (copyPoint != null) {
      myCallback.consume(copyPoint);
    }
    else {
      myMergeProcess.end("Merge start wasn't found", true);
    }
  }
}
