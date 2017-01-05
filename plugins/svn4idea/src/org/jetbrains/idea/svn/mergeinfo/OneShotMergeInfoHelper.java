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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.LogEntry;
import org.jetbrains.idea.svn.history.LogEntryPath;
import org.jetbrains.idea.svn.history.LogHierarchyNode;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.toUpperCase;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Collections.reverseOrder;
import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache.MergeCheckResult;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.isAncestor;

public class OneShotMergeInfoHelper implements MergeChecker {

  @NotNull private final MergeContext myMergeContext;
  @NotNull private final Map<Long, Collection<String>> myPartiallyMerged;
  // subpath [file] (local) to (subpathURL - merged FROM - to ranges list)
  @NotNull private final NavigableMap<String, Map<String, SVNMergeRangeList>> myMergeInfoMap;
  @NotNull private final Object myMergeInfoLock;

  public OneShotMergeInfoHelper(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myPartiallyMerged = newHashMap();
    myMergeInfoLock = new Object();
    myMergeInfoMap = new TreeMap<>(reverseOrder());
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
  public MergeCheckResult checkList(@NotNull SvnChangeList changeList) {
    Set<String> notMergedPaths = newHashSet();
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
           ? hasMergedPaths ? MergeCheckResult.MERGED : MergeCheckResult.NOT_EXISTS
           : MergeCheckResult.NOT_MERGED;
  }

  @NotNull
  public MergeCheckResult checkPath(@NotNull String repositoryRelativePath, long revisionNumber) {
    MergeCheckResult result = MergeCheckResult.NOT_EXISTS;
    String sourceRelativePath =
      SVNPathUtil.getRelativePath(myMergeContext.getRepositoryRelativeSourcePath(), ensureStartSlash(repositoryRelativePath));

    // TODO: SVNPathUtil.getRelativePath() is @NotNull - probably we need to check also isEmpty() here?
    if (sourceRelativePath != null) {
      InfoProcessor processor = new InfoProcessor(sourceRelativePath, myMergeContext.getRepositoryRelativeSourcePath(), revisionNumber);
      String key = toKey(sourceRelativePath);

      synchronized (myMergeInfoLock) {
        Map<String, SVNMergeRangeList> mergeInfo = myMergeInfoMap.get(key);
        if (mergeInfo != null) {
          processor.process(key, mergeInfo);
        }
        else {
          for (Map.Entry<String, Map<String, SVNMergeRangeList>> entry : myMergeInfoMap.tailMap(key).entrySet()) {
            if (isUnder(entry.getKey(), key) && processor.process(entry.getKey(), entry.getValue())) {
              break;
            }
          }
        }
      }

      result = MergeCheckResult.getInstance(processor.isMerged());
    }

    return result;
  }

  private static boolean isUnder(@NotNull String parentUrl, @NotNull String childUrl) {
    return ".".equals(parentUrl) || isAncestor(ensureStartSlash(parentUrl), ensureStartSlash(childUrl));
  }

  private static class InfoProcessor implements PairProcessor<String, Map<String, SVNMergeRangeList>> {

    @NotNull private final String myRepositoryRelativeSourcePath;
    private boolean myIsMerged;
    @NotNull private final String mySourceRelativePath;
    private final long myRevisionNumber;

    public InfoProcessor(@NotNull String sourceRelativePath, @NotNull String repositoryRelativeSourcePath, long revisionNumber) {
      mySourceRelativePath = sourceRelativePath;
      myRevisionNumber = revisionNumber;
      myRepositoryRelativeSourcePath = ensureStartSlash(repositoryRelativeSourcePath);
    }

    public boolean isMerged() {
      return myIsMerged;
    }

    // TODO: Try to unify with BranchInfo.processMergeinfoProperty()
    public boolean process(@NotNull String workingCopyRelativePath, @NotNull Map<String, SVNMergeRangeList> mergedPathsMap) {
      boolean processed = false;
      boolean isCurrentPath = workingCopyRelativePath.equals(mySourceRelativePath);

      if (mergedPathsMap.isEmpty()) {
        myIsMerged = false;
        processed = true;
      }
      else {
        String mergedPathAffectingSourcePath =
          find(mergedPathsMap.keySet(), path -> isAncestor(myRepositoryRelativeSourcePath, ensureStartSlash(path)));

        if (mergedPathAffectingSourcePath != null) {
          SVNMergeRangeList mergeRangeList = mergedPathsMap.get(mergedPathAffectingSourcePath);

          processed = true;
          myIsMerged = exists(mergeRangeList.getRanges(),
                              range -> BranchInfo.isInRange(range, myRevisionNumber) && (range.isInheritable() || isCurrentPath));
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
        Map<String, SVNMergeRangeList> mergeInfo = parseMergeInfo(property);

        synchronized (myMergeInfoLock) {
          myMergeInfoMap.put(toKey(workingCopyRelativePath), mergeInfo);
        }
      }

      public void handleProperty(SVNURL url, PropertyData property) throws SVNException {
      }

      public void handleProperty(long revision, PropertyData property) throws SVNException {
      }

      @NotNull
      private Map<String, SVNMergeRangeList> parseMergeInfo(@NotNull PropertyData property) throws SVNException {
        try {
          return BranchInfo.parseMergeInfo(notNull(property.getValue()));
        }
        catch (SvnBindException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, e), e);
        }
      }
    };
  }

  @NotNull
  private String getWorkingCopyRelativePath(@NotNull File file) {
    return toSystemIndependentName(notNull(getRelativePath(myMergeContext.getWcInfo().getRootInfo().getIoFile(), file)));
  }

  @NotNull
  private static String toKey(@NotNull String path) {
    return SystemInfo.isFileSystemCaseSensitive ? path : toUpperCase(path);
  }

  // true if errors found
  public boolean checkListForPaths(@NotNull LogHierarchyNode node) {
    // TODO: Such filtering logic is not clear enough so far (and probably not correct for all cases - for instance when we perform merge
    // TODO: from branch1 to branch2 and have revision which contain merge changes from branch3 to branch1.
    // TODO: In this case paths of child log entries will not contain neither urls from branch1 nor from branch2 - and checkEntry() method
    // TODO: will return true => so such revision will not be used (and displayed) further.

    // TODO: Why do we check entries recursively - we have a revision - set of changes in the "merge from" branch? Why do we need to check
    // TODO: where they came from - we want avoid some circular merges or what? Does subversion itself perform such checks or not?
    boolean isLocalChange = or(node.getChildren(), this::checkForSubtree);

    return isLocalChange ||
           checkForEntry(node.getMe(), myMergeContext.getRepositoryRelativeWorkingCopyPath(),
                         myMergeContext.getRepositoryRelativeSourcePath());
  }

  /**
   * TODO: Why checkForEntry() from checkListForPaths() and checkForSubtree() are called with swapped parameters.
   */
  // true if errors found
  private boolean checkForSubtree(@NotNull LogHierarchyNode tree) {
    LinkedList<LogHierarchyNode> queue = new LinkedList<>();
    queue.addLast(tree);

    while (!queue.isEmpty()) {
      LogHierarchyNode element = queue.removeFirst();
      ProgressManager.checkCanceled();

      if (checkForEntry(element.getMe(), myMergeContext.getRepositoryRelativeSourcePath(),
                        myMergeContext.getRepositoryRelativeWorkingCopyPath())) {
        return true;
      }
      queue.addAll(element.getChildren());
    }
    return false;
  }

  // true if errors found
  // checks if either some changed path is in current branch => treat as local change
  // or if no changed paths in current branch, checks if at least one path in "merge from" branch
  // NOTE: this fails for "merge-source" log entries from other branches - when all changed paths are from some
  // third branch - this logic treats such log entry as local.
  private static boolean checkForEntry(@NotNull LogEntry entry, @NotNull String localURL, @NotNull String relativeBranch) {
    boolean atLeastOneUnderBranch = false;

    for (LogEntryPath path : entry.getChangedPaths().values()) {
      if (isAncestor(localURL, path.getPath())) {
        return true;
      }
      if (!atLeastOneUnderBranch && isAncestor(relativeBranch, path.getPath())) {
        atLeastOneUnderBranch = true;
      }
    }
    return !atLeastOneUnderBranch;
  }
}
