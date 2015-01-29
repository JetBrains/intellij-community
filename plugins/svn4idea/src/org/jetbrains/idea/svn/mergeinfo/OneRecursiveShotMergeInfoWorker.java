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
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AreaMap;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Map;

public class OneRecursiveShotMergeInfoWorker {

  @NotNull private final MergeContext myMergeContext;

  // subpath [file] (local) to (subpathURL - merged FROM - to ranges list)
  @NotNull private final AreaMap<String, Map<String, SVNMergeRangeList>> myDataMap;
  @NotNull private final Object myLock;
  @NotNull private final String myFromUrlRelative;

  public OneRecursiveShotMergeInfoWorker(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myLock = new Object();
    // TODO: Rewrite without AreaMap usage
    myDataMap = AreaMap.create(new PairProcessor<String, String>() {
      public boolean process(String parentUrl, String childUrl) {
        if (".".equals(parentUrl)) return true;
        return SVNPathUtil.isAncestor(SvnUtil.ensureStartSlash(parentUrl), SvnUtil.ensureStartSlash(childUrl));
      }
    });
    myFromUrlRelative =
      SvnUtil.ensureStartSlash(SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl()));
  }

  public void prepare() throws VcsException {
    Depth depth = Depth.allOrEmpty(myMergeContext.getVcs().getSvnConfiguration().isCheckNestedForQuickMerge());
    File file = myMergeContext.getWcInfo().getRootInfo().getIoFile();

    myMergeContext.getVcs().getFactory(file).createPropertyClient()
      .getProperty(SvnTarget.fromFile(file), SvnPropertyKeys.MERGE_INFO, SVNRevision.WORKING, depth, createPropertyHandler());
  }

  @NotNull
  private PropertyConsumer createPropertyHandler() {
    return new PropertyConsumer() {
      public void handleProperty(@NotNull File path, @NotNull PropertyData property) throws SVNException {
        String workingCopyRelativePath = getWorkingCopyRelativePath(path);
        Map<String, SVNMergeRangeList> mergeInfo;

        try {
          mergeInfo = BranchInfo.parseMergeInfo(ObjectUtils.assertNotNull(property.getValue()));
        }
        catch (SvnBindException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, e), e);
        }

        synchronized (myLock) {
          myDataMap.put(toKey(workingCopyRelativePath), mergeInfo);
        }
      }

      public void handleProperty(SVNURL url, PropertyData property) throws SVNException {
      }

      public void handleProperty(long revision, PropertyData property) throws SVNException {
      }
    };
  }

  @NotNull
  public SvnMergeInfoCache.MergeCheckResult isMerged(@NotNull String relativeToRepoURLPath, long revisionNumber) {
    String relativeToWc = SVNPathUtil.getRelativePath(myFromUrlRelative, SvnUtil.ensureStartSlash(relativeToRepoURLPath));
    SvnMergeInfoCache.MergeCheckResult result;

    if (relativeToWc == null) {
      // TODO: SVNPathUtil.getRelativePath() is @NotNull - probably we need to check also isEmpty() here?
      result = SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    }
    else {
      InfoProcessor processor = new InfoProcessor(relativeToWc, myFromUrlRelative, revisionNumber);

      synchronized (myLock) {
        myDataMap.getSimiliar(toKey(relativeToWc), processor);
      }

      result = SvnMergeInfoCache.MergeCheckResult.getInstance(processor.isMerged());
    }

    return result;
  }

  private static class InfoProcessor implements PairProcessor<String, Map<String, SVNMergeRangeList>> {

    @NotNull private final String myWcLevelRelativeSourceUrl;
    private boolean myMerged;
    @NotNull private final String myFilePathAsked;
    private final long myRevisionAsked;

    public InfoProcessor(@NotNull String filePathAsked, @NotNull String wcLevelRelativeSourceUrl, long revisionAsked) {
      myFilePathAsked = filePathAsked;
      myRevisionAsked = revisionAsked;
      myWcLevelRelativeSourceUrl = SvnUtil.ensureStartSlash(wcLevelRelativeSourceUrl);
    }

    public boolean isMerged() {
      return myMerged;
    }

    // TODO: Try to unify with BranchInfo.processMergeinfoProperty()
    public boolean process(@NotNull String relativeFileSubpath, @NotNull Map<String, SVNMergeRangeList> map) {
      boolean processed = false;
      final boolean self = relativeFileSubpath.equals(myFilePathAsked);

      if (map.isEmpty()) {
        myMerged = false;
        processed = true;
      }
      else {
        String mergedPathAffectingSourcePath = ContainerUtil.find(map.keySet(), new Condition<String>() {
          @Override
          public boolean value(String path) {
            return SVNPathUtil.isAncestor(myWcLevelRelativeSourceUrl, SvnUtil.ensureStartSlash(path));
          }
        });

        if (mergedPathAffectingSourcePath != null) {
          SVNMergeRangeList mergeRangeList = map.get(mergedPathAffectingSourcePath);

          processed = true;
          myMerged = ContainerUtil.or(mergeRangeList.getRanges(), new Condition<SVNMergeRange>() {
            @Override
            public boolean value(@NotNull SVNMergeRange range) {
              return BranchInfo.isInRange(range, myRevisionAsked) && (range.isInheritable() || self);
            }
          });
        }
      }

      return processed;
    }
  }

  @NotNull
  private String getWorkingCopyRelativePath(@NotNull File file) {
    return FileUtil.toSystemIndependentName(
      ObjectUtils.assertNotNull(FileUtil.getRelativePath(myMergeContext.getWcInfo().getRootInfo().getIoFile(), file)));
  }

  @NotNull
  private static String toKey(@NotNull String path) {
    return SystemInfo.isFileSystemCaseSensitive ? path : path.toUpperCase();
  }
}
