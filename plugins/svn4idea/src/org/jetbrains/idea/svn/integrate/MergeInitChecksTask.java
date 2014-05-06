/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeInitChecksTask extends BaseMergeTask {

  public MergeInitChecksTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "initial checks", Where.AWT);
  }

  @Override
  public void run(ContinuationContext continuationContext) {
    SVNURL url = parseUrl(continuationContext);
    if (url == null) {
      return;
    }

    if (SVNURLUtil.isAncestor(url, myMergeContext.getWcInfo().getUrl()) ||
        SVNURLUtil.isAncestor(myMergeContext.getWcInfo().getUrl(), url)) {
      finishWithError(continuationContext, "Cannot merge from self", true);
      return;
    }

    if (!checkForSwitchedRoots()) {
      continuationContext.cancelEverything();
    }
  }

  private boolean checkForSwitchedRoots() {
    final List<WCInfo> infoList = myMergeContext.getVcs().getAllWcInfos();
    boolean switchedFound = false;
    for (WCInfo wcInfo : infoList) {
      if (FileUtil.isAncestor(new File(myMergeContext.getWcInfo().getPath()), new File(wcInfo.getPath()), true)
          && NestedCopyType.switched.equals(wcInfo.getType())) {
        switchedFound = true;
        break;
      }
    }
    if (switchedFound) {
      return myInteraction.shouldContinueSwitchedRootFound();
    }
    return true;
  }

  @Nullable
  private SVNURL parseUrl(ContinuationContext continuationContext) {
    SVNURL url = null;

    try {
      url = SvnUtil.createUrl(myMergeContext.getSourceUrl());
    }
    catch (SvnBindException e) {
      finishWithError(continuationContext, e.getMessage(), true);
    }

    return url;
  }
}
