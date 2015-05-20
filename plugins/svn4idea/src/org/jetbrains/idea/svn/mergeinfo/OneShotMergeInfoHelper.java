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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class OneShotMergeInfoHelper implements MergeChecker {

  @NotNull private final MergeContext myMergeContext;
  @NotNull private final Map<Long, Collection<String>> myPartiallyMerged;
  // subpath [file] (local) to (subpathURL - merged FROM - to ranges list)
  @NotNull private final AreaMap<String, Map<String, SVNMergeRangeList>> myMergeInfoMap;
  @NotNull private final Object myMergeInfoLock;

  public OneShotMergeInfoHelper(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myPartiallyMerged = ContainerUtil.newHashMap();
    myMergeInfoLock = new Object();
    // TODO: Rewrite without AreaMap usage
    myMergeInfoMap = AreaMap.create(new PairProcessor<String, String>() {
      public boolean process(String parentUrl, String childUrl) {
        if (".".equals(parentUrl)) return true;
        return SVNPathUtil.isAncestor(SvnUtil.ensureStartSlash(parentUrl), SvnUtil.ensureStartSlash(childUrl));
      }
    });
  }

  @Override
  public void prepare() throws VcsException {
    Depth depth = Depth.allOrEmpty(myMergeContext.getVcs().getSvnConfiguration().isCheckNestedForQuickMerge());
    File file = myMergeContext.getWcInfo().getRootInfo().getIoFile();

    myMergeContext.getVcs().getFactory(file).createPropertyClient()
      .getProperty(SvnTarget.fromFile(file), SvnPropertyKeys.MERGE_INFO, SVNRevision.WORKING, depth, createPropertyHandler());
  }

  @Nullable
  public Collection<String> getNotMergedPaths(@NotNull SvnChangeList changeList) {
    return myPartiallyMerged.get(changeList.getNumber());
  }

  @NotNull
  public SvnMergeInfoCache.MergeCheckResult checkList(@NotNull SvnChangeList changeList) {
    Set<String> notMergedPaths = ContainerUtil.newHashSet();
    boolean hasMergedPaths = false;

    for (String path : changeList.getAffectedPaths()) {
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (checkPath(path, changeList.getNumber())) {
        case MERGED:
          hasMergedPaths = true;
          break;
        case NOT_MERGED:
          notMergedPaths.add(path);
          break;
      }
    }

    if (hasMergedPaths && !notMergedPaths.isEmpty()) {
      myPartiallyMerged.put(changeList.getNumber(), notMergedPaths);
    }

    return notMergedPaths.isEmpty()
           ? hasMergedPaths ? SvnMergeInfoCache.MergeCheckResult.MERGED : SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS
           : SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
  }

  @NotNull
  public SvnMergeInfoCache.MergeCheckResult checkPath(@NotNull String repositoryRelativePath, long revisionNumber) {
    String sourceRelativePath =
      SVNPathUtil.getRelativePath(myMergeContext.getRepositoryRelativeSourcePath(), SvnUtil.ensureStartSlash(repositoryRelativePath));
    SvnMergeInfoCache.MergeCheckResult result;

    if (sourceRelativePath == null) {
      // TODO: SVNPathUtil.getRelativePath() is @NotNull - probably we need to check also isEmpty() here?
      result = SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    }
    else {
      InfoProcessor processor = new InfoProcessor(sourceRelativePath, myMergeContext.getRepositoryRelativeSourcePath(), revisionNumber);

      synchronized (myMergeInfoLock) {
        myMergeInfoMap.getSimiliar(toKey(sourceRelativePath), processor);
      }

      result = SvnMergeInfoCache.MergeCheckResult.getInstance(processor.isMerged());
    }

    return result;
  }

  private static class InfoProcessor implements PairProcessor<String, Map<String, SVNMergeRangeList>> {

    @NotNull private final String myRepositoryRelativeSourcePath;
    private boolean myIsMerged;
    @NotNull private final String mySourceRelativePath;
    private final long myRevisionNumber;

    public InfoProcessor(@NotNull String sourceRelativePath, @NotNull String repositoryRelativeSourcePath, long revisionNumber) {
      mySourceRelativePath = sourceRelativePath;
      myRevisionNumber = revisionNumber;
      myRepositoryRelativeSourcePath = SvnUtil.ensureStartSlash(repositoryRelativeSourcePath);
    }

    public boolean isMerged() {
      return myIsMerged;
    }

    // TODO: Try to unify with BranchInfo.processMergeinfoProperty()
    public boolean process(@NotNull String workingCopyRelativePath, @NotNull Map<String, SVNMergeRangeList> mergedPathsMap) {
      boolean processed = false;
      final boolean isCurrentPath = workingCopyRelativePath.equals(mySourceRelativePath);

      if (mergedPathsMap.isEmpty()) {
        myIsMerged = false;
        processed = true;
      }
      else {
        String mergedPathAffectingSourcePath = ContainerUtil.find(mergedPathsMap.keySet(), new Condition<String>() {
          @Override
          public boolean value(String path) {
            return SVNPathUtil.isAncestor(myRepositoryRelativeSourcePath, SvnUtil.ensureStartSlash(path));
          }
        });

        if (mergedPathAffectingSourcePath != null) {
          SVNMergeRangeList mergeRangeList = mergedPathsMap.get(mergedPathAffectingSourcePath);

          processed = true;
          myIsMerged = ContainerUtil.or(mergeRangeList.getRanges(), new Condition<SVNMergeRange>() {
            @Override
            public boolean value(@NotNull SVNMergeRange range) {
              return BranchInfo.isInRange(range, myRevisionNumber) && (range.isInheritable() || isCurrentPath);
            }
          });
        }
      }

      return processed;
    }
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

        synchronized (myMergeInfoLock) {
          myMergeInfoMap.put(toKey(workingCopyRelativePath), mergeInfo);
        }
      }

      public void handleProperty(SVNURL url, PropertyData property) throws SVNException {
      }

      public void handleProperty(long revision, PropertyData property) throws SVNException {
      }
    };
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
