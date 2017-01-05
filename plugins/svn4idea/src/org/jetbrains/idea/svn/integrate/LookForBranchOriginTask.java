/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

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
    SVNURL repoUrl = myMergeContext.getWcInfo().getRootInfo().getRepositoryUrlUrl();
    String sourceUrl = myFromSource ? myMergeContext.getSourceUrl() : myMergeContext.getWcInfo().getRootUrl();
    String targetUrl = myFromSource ? myMergeContext.getWcInfo().getRootUrl() : myMergeContext.getSourceUrl();
    SvnBranchPointsCalculator.WrapperInvertor copyPoint =
      myMergeContext.getVcs().getSvnBranchPointsCalculator().calculateCopyPoint(repoUrl, sourceUrl, targetUrl);

    if (copyPoint != null) {
      myCallback.consume(copyPoint);
    }
    else {
      myMergeProcess.end("Merge start wasn't found", true);
    }
  }
}
