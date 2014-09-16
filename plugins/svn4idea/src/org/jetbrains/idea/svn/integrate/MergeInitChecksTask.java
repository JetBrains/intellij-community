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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeInitChecksTask extends BaseMergeTask {

  public MergeInitChecksTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "initial checks", Where.AWT);
  }

  @Override
  public void run(ContinuationContext context) {
    SVNURL url = parseSourceUrl(context);

    if (url != null) {
      if (areInSameHierarchy(url, myMergeContext.getWcInfo().getUrl())) {
        finishWithError(context, "Cannot merge from self", true);
      }
      else if (hasSwitchedRoots() && !myInteraction.shouldContinueSwitchedRootFound()) {
        context.cancelEverything();
      }
    }
  }

  private boolean hasSwitchedRoots() {
    final File currentRoot = myMergeContext.getWcInfo().getRootInfo().getIoFile();

    return ContainerUtil.or(myMergeContext.getVcs().getAllWcInfos(), new Condition<WCInfo>() {
      @Override
      public boolean value(WCInfo info) {
        return NestedCopyType.switched.equals(info.getType()) && FileUtil.isAncestor(currentRoot, info.getRootInfo().getIoFile(), true);
      }
    });
  }

  private static boolean areInSameHierarchy(@NotNull SVNURL url1, @NotNull SVNURL url2) {
    return SVNURLUtil.isAncestor(url1, url2) || SVNURLUtil.isAncestor(url2, url1);
  }
}
