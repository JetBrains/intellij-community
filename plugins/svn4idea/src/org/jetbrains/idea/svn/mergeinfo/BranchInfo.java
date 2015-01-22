/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;

public class BranchInfo {

  private final static Logger LOG = Logger.getInstance(BranchInfo.class);
  // repo path in branch in format path@revision -> merged revisions
  @NotNull private final Map<String, Set<Long>> myPathMergedMap;
  @NotNull private final Map<String, Set<Long>> myNonInheritablePathMergedMap;

  private boolean myMixedRevisionsFound;

  // revision in trunk -> whether merged into branch
  @NotNull private final Map<Long, SvnMergeInfoCache.MergeCheckResult> myAlreadyCalculatedMap;
  @NotNull private final Object myCalculatedLock = new Object();

  @NotNull private final WCInfoWithBranches myInfo;
  @NotNull private final WCInfoWithBranches.Branch myBranch;
  @NotNull private final SvnVcs myVcs;

  private SvnMergeInfoCache.CopyRevison myCopyRevison;
  @NotNull private final MultiMap<Long, String> myPartlyMerged;

  public BranchInfo(@NotNull SvnVcs vcs, @NotNull WCInfoWithBranches info, @NotNull WCInfoWithBranches.Branch branch) {
    myVcs = vcs;
    myInfo = info;
    myBranch = branch;

    myPathMergedMap = ContainerUtil.newHashMap();
    myPartlyMerged = MultiMap.create();
    myNonInheritablePathMergedMap = ContainerUtil.newHashMap();

    myAlreadyCalculatedMap = ContainerUtil.newHashMap();
  }

  private long calculateCopyRevision(final String branchPath) {
    if (myCopyRevison != null && Comparing.equal(myCopyRevison.getPath(), branchPath)) {
      return myCopyRevison.getRevision();
    }
    myCopyRevison = new SvnMergeInfoCache.CopyRevison(myVcs, branchPath, myInfo.getRepoUrl(), myBranch.getUrl(), myInfo.getRootUrl());
    return -1;
  }

  public void clear() {
    myPathMergedMap.clear();
    synchronized (myCalculatedLock) {
      myAlreadyCalculatedMap.clear();
    }
    myMixedRevisionsFound = false;
  }

  @NotNull
  public MergeInfoCached getCached() {
    synchronized (myCalculatedLock) {
      long revision = myCopyRevison != null ? myCopyRevison.getRevision() : -1;

      // TODO: NEW MAP WILL ALSO BE CREATED IN MergeInfoCached constructor
      return new MergeInfoCached(Collections.unmodifiableMap(myAlreadyCalculatedMap), revision);
    }
  }

  @NotNull
  public SvnMergeInfoCache.MergeCheckResult checkList(@NotNull final SvnChangeList list, final String branchPath) {
    synchronized (myCalculatedLock) {
      SvnMergeInfoCache.MergeCheckResult result;
      final long revision = calculateCopyRevision(branchPath);
      if (revision != -1 && revision >= list.getNumber()) {
        result = SvnMergeInfoCache.MergeCheckResult.COMMON;
      }
      else {
        result = ContainerUtil.getOrCreate(myAlreadyCalculatedMap, list.getNumber(), new Factory<SvnMergeInfoCache.MergeCheckResult>() {
          @Override
          public SvnMergeInfoCache.MergeCheckResult create() {
            return checkAlive(list, branchPath);
          }
        });
      }
      return result;
    }
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult checkAlive(@NotNull SvnChangeList list, @NotNull String branchPath) {
    final Info info = myVcs.getInfo(new File(branchPath));
    if (info == null || info.getURL() == null || !SVNPathUtil.isAncestor(myBranch.getUrl(), info.getURL().toString())) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    final String subPathUnderBranch = SVNPathUtil.getRelativePath(myBranch.getUrl(), info.getURL().toString());

    MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result = MultiMap.create();
    checkPaths(list.getNumber(), list.getAddedPaths(), branchPath, subPathUnderBranch, result);
    if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS)) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    }
    checkPaths(list.getNumber(), list.getDeletedPaths(), branchPath, subPathUnderBranch, result);
    if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS)) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    }
    checkPaths(list.getNumber(), list.getChangedPaths(), branchPath, subPathUnderBranch, result);

    if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS)) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    } else if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)) {
      myPartlyMerged.put(list.getNumber(), result.get(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED));
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    return SvnMergeInfoCache.MergeCheckResult.MERGED;
  }

  private void checkPaths(final long number, @NotNull Collection<String> paths, @NotNull String branchPath, final String subPathUnderBranch,
                          @NotNull MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result) {
    String myTrunkPathCorrespondingToLocalBranchPath = SVNPathUtil.append(myInfo.getCurrentBranch().getUrl(), subPathUnderBranch);

    for (String path : paths) {
      String absoluteInTrunkPath = SVNPathUtil.append(myInfo.getRepoUrl(), path);
      SvnMergeInfoCache.MergeCheckResult mergeCheckResult;

      if (!absoluteInTrunkPath.startsWith(myTrunkPathCorrespondingToLocalBranchPath)) {
        mergeCheckResult = SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
      }
      else {
        String relativeToTrunkPath = absoluteInTrunkPath.substring(myTrunkPathCorrespondingToLocalBranchPath.length());
        String localPathInBranch = new File(branchPath, relativeToTrunkPath).getAbsolutePath();

        mergeCheckResult = checkPathGoingUp(number, -1, branchPath, localPathInBranch, path, true);
      }

      result.putValue(mergeCheckResult, path);
    }
  }

  private SvnMergeInfoCache.MergeCheckResult goUp(final long revisionAsked, final long targetRevision, final String branchRootPath,
                                                  final String path, @NotNull String trunkUrl) {
    final String newTrunkUrl = SVNPathUtil.removeTail(trunkUrl).trim();
    if (newTrunkUrl.length() == 0 || "/".equals(newTrunkUrl)) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    final String newPath = new File(path).getParent();
    if (newPath.length() < branchRootPath.length()) {
      // we are higher than WC root -> go into repo only
      if (targetRevision == -1) {
        // no paths in local copy
        return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
      }
      final Info svnInfo = myVcs.getInfo(new File(branchRootPath));
      if (svnInfo == null || svnInfo.getURL() == null) {
        return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
      try {
        return goUpInRepo(revisionAsked, targetRevision, svnInfo.getURL().removePathTail(), newTrunkUrl);
      }
      catch (SVNException e) {
        LOG.info(e);
        return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
    }
    
    return checkPathGoingUp(revisionAsked, targetRevision, branchRootPath, newPath, newTrunkUrl, false);
  }

  private SvnMergeInfoCache.MergeCheckResult goUpInRepo(final long revisionAsked, final long targetRevision, final SVNURL branchUrl,
                                                        final String trunkUrl) {
    final String branchAsString = branchUrl.toString();
    final String keyString = branchAsString + "@" + targetRevision;
    final Set<Long> mergeInfo = myPathMergedMap.get(keyString);
    if (mergeInfo != null) {
      // take from self or first parent with info; do not go further
      return SvnMergeInfoCache.MergeCheckResult.getInstance(mergeInfo.contains(revisionAsked));
    }

    final PropertyValue mergeinfoProperty;
    SvnTarget target = SvnTarget.fromURL(branchUrl);

    try {
      mergeinfoProperty = myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false,
                                                                                      SVNRevision.create(targetRevision));
    }
    catch (VcsException e) {
      LOG.info(e);
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    if (mergeinfoProperty == null) {
      final String newTrunkUrl = SVNPathUtil.removeTail(trunkUrl).trim();
      final SVNURL newBranchUrl;
      try {
        newBranchUrl = branchUrl.removePathTail();
      }
      catch (SVNException e) {
        LOG.info(e);
        return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
      final String absoluteTrunk = SVNPathUtil.append(myInfo.getRepoUrl(), newTrunkUrl);
      if ((1 >= newTrunkUrl.length()) || (myInfo.getRepoUrl().length() >= newBranchUrl.toString().length()) ||
        (newBranchUrl.toString().equals(absoluteTrunk))) {
        return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
      // go up
      return goUpInRepo(revisionAsked, targetRevision, newBranchUrl, newTrunkUrl);
    }
    // process
    return processMergeinfoProperty(keyString, revisionAsked, mergeinfoProperty, trunkUrl, false);
  }

  private SvnMergeInfoCache.MergeCheckResult checkPathGoingUp(final long revisionAsked,
                                                              final long targetRevision,
                                                              @NotNull String branchRootPath,
                                                              @NotNull String path,
                                                              final String trunkUrl,
                                                              final boolean self) {
    final File pathFile = new File(path);

    // we didn't find existing item on the path jet
    // check whether we locally have path
    if (targetRevision == -1 && !pathFile.exists()) {
      // go into parent
      return goUp(revisionAsked, targetRevision, branchRootPath, path, trunkUrl);
    }

    final Info svnInfo = myVcs.getInfo(pathFile);
    if (svnInfo == null || svnInfo.getURL() == null) {
      LOG.info("Svninfo for " + pathFile + " is null or not full.");
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    final long actualRevision = svnInfo.getRevision().getNumber();
    final long targetRevisionCorrected = (targetRevision == -1) ? actualRevision : targetRevision;
    
    // here we know local URL and revision

    // check existing info
    final String keyString = path + "@" + targetRevisionCorrected;
    final Set<Long> selfInfo = self ? myNonInheritablePathMergedMap.get(keyString) : null;
    final Set<Long> mergeInfo = myPathMergedMap.get(keyString);
    if (mergeInfo != null || selfInfo != null) {
      final boolean merged = mergeInfo != null && mergeInfo.contains(revisionAsked) || selfInfo != null && selfInfo.contains(revisionAsked);
      // take from self or first parent with info; do not go further 
      return SvnMergeInfoCache.MergeCheckResult.getInstance(merged);
    }

    if (actualRevision != targetRevisionCorrected) {
      myMixedRevisionsFound = true;
    }

    SvnTarget target;
    SVNRevision revision;
    if (actualRevision == targetRevisionCorrected) {
      // look in WC
      target = SvnTarget.fromFile(pathFile, SVNRevision.WORKING);
      revision = SVNRevision.WORKING;
    }
    else {
      // in repo
      target = SvnTarget.fromURL(svnInfo.getURL());
      revision = SVNRevision.create(targetRevisionCorrected);
    }

    PropertyValue mergeinfoProperty;
    try {
      mergeinfoProperty = myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, revision);
    }
    catch (VcsException e) {
      LOG.info(e);
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    return mergeinfoProperty == null
           ? goUp(revisionAsked, targetRevisionCorrected, branchRootPath, path, trunkUrl)
           : processMergeinfoProperty(keyString, revisionAsked, mergeinfoProperty, trunkUrl, self);
  }

  private SvnMergeInfoCache.MergeCheckResult processMergeinfoProperty(final String pathWithRevisionNumber, final long revisionAsked,
                                                                      @NotNull PropertyValue value, final String trunkRelativeUrl,
                                                                      final boolean self) {
    if (value.toString().trim().length() == 0) {
      myPathMergedMap.put(pathWithRevisionNumber, Collections.<Long>emptySet());
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    final Map<String, SVNMergeRangeList> map;
    try {
      map = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(replaceSeparators(value.toString())), null);
    }
    catch (SVNException e) {
      LOG.info(e);
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    for (String key : map.keySet()) {
      if ((key != null) && (trunkRelativeUrl.startsWith(key))) {
        final Set<Long> revisions = new HashSet<Long>();
        final Set<Long> nonInheritableRevisions = new HashSet<Long>();

        final SVNMergeRangeList rangesList = map.get(key);

        boolean result = false;
        for (SVNMergeRange range : rangesList.getRanges()) {
          // SVN does not include start revision in range
          final long startRevision = range.getStartRevision() + 1;
          final long endRevision = range.getEndRevision();
          final boolean isInheritable = range.isInheritable();
          final boolean inInterval = (revisionAsked >= startRevision) && (revisionAsked <= endRevision);

          if ((isInheritable || self) && inInterval) {
            result = true;
          }

          for (long i = startRevision; i <= endRevision; i++) {
            if (isInheritable) {
              revisions.add(i);
            } else {
              nonInheritableRevisions.add(i);
            }
          }
        }
        myPathMergedMap.put(pathWithRevisionNumber, revisions);
        if (! nonInheritableRevisions.isEmpty()) {
          myNonInheritablePathMergedMap.put(pathWithRevisionNumber, nonInheritableRevisions);
        }

        return SvnMergeInfoCache.MergeCheckResult.getInstance(result);
      }
    }
    myPathMergedMap.put(pathWithRevisionNumber, Collections.<Long>emptySet());
    return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
  }

  @NotNull
  private static String replaceSeparators(@NotNull String s) {
    return s.replace('\r', '\n').replace("\n\n", "\n");
  }

  public boolean isMixedRevisionsFound() {
    return myMixedRevisionsFound;
  }

  // if nothing, maybe all not merged or merged: here only partly not merged
  @SuppressWarnings("unused")
  @NotNull
  public Collection<String> getNotMergedPaths(final long number) {
    return myPartlyMerged.get(number);
  }
}
