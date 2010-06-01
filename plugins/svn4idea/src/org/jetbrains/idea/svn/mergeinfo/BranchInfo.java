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
import com.intellij.util.containers.MultiMap;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

public class BranchInfo {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.mergeinfo.BranchInfo");
  // repo path in branch in format path@revision -> merged revisions
  private final Map<String, Set<Long>> myPathMergedMap;
  private final Map<String, Set<Long>> myNonInheritablePathMergedMap;

  private boolean myMixedRevisionsFound;

  // revision in trunk -> whether merged into branch
  private final Map<Long, SvnMergeInfoCache.MergeCheckResult> myAlreadyCalculatedMap;
  private final Object myCalculatedLock = new Object();

  private final String myRepositoryRoot;
  private final String myBranchUrl;
  private final String myTrunkUrl;
  private final String myTrunkCorrected;
  private final SVNWCClient myClient;
  private final SvnVcs myVcs;

  private SvnMergeInfoCache.CopyRevison myCopyRevison;
  private final MultiMap<Long, String> myPartlyMerged;

  public BranchInfo(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl,
                     final String trunkCorrected, final SVNWCClient client) {
    myVcs = vcs;
    myRepositoryRoot = repositoryRoot;
    myBranchUrl = branchUrl;
    myTrunkUrl = trunkUrl;
    myTrunkCorrected = trunkCorrected;
    myClient = client;

    myPathMergedMap = new HashMap<String, Set<Long>>();
    myPartlyMerged = new MultiMap<Long, String>();
    myNonInheritablePathMergedMap = new HashMap<String, Set<Long>>();

    myAlreadyCalculatedMap = new HashMap<Long, SvnMergeInfoCache.MergeCheckResult>();
  }

  private long calculateCopyRevision(final String branchPath) {
    if (myCopyRevison != null && Comparing.equal(myCopyRevison.getPath(), branchPath)) {
      return myCopyRevison.getRevision();
    }
    myCopyRevison = new SvnMergeInfoCache.CopyRevison(myVcs, branchPath, myRepositoryRoot, myBranchUrl, myTrunkUrl);
    return -1;
  }

  public void clear() {
    myPathMergedMap.clear();
    synchronized (myCalculatedLock) {
      myAlreadyCalculatedMap.clear();
    }
    myMixedRevisionsFound = false;
  }

  public void halfClear(final long listNumber) {
    myPathMergedMap.clear();
    synchronized (myCalculatedLock) {
      myAlreadyCalculatedMap.remove(listNumber);
    }
    myMixedRevisionsFound = false;
  }

  public MergeinfoCached getCached() {
    synchronized (myCalculatedLock) {
      final long revision;
      if (myCopyRevison != null && myCopyRevison.getRevision() != -1) {
        revision = myCopyRevison.getRevision();
      } else {
        revision = -1;
      }
      return new MergeinfoCached(Collections.unmodifiableMap(myAlreadyCalculatedMap), revision);
    }
  }

  public SvnMergeInfoCache.MergeCheckResult checkList(final SvnChangeList list, final String branchPath) {
    synchronized (myCalculatedLock) {
      final long revision = calculateCopyRevision(branchPath);
      if (revision != -1 && revision >= list.getNumber()) {
        return SvnMergeInfoCache.MergeCheckResult.COMMON;
      }

      final SvnMergeInfoCache.MergeCheckResult calculated = myAlreadyCalculatedMap.get(list.getNumber());
      if (calculated != null) {
        return calculated;
      }

      final SvnMergeInfoCache.MergeCheckResult result = checkAlive(list, branchPath);
      myAlreadyCalculatedMap.put(list.getNumber(), result);
      return result;
    }
  }

  private SvnMergeInfoCache.MergeCheckResult checkAlive(final SvnChangeList list, final String branchPath) {
    final SVNInfo info = getInfo(new File(branchPath));
    if (info == null || info.getURL() == null || (! SVNPathUtil.isAncestor(myBranchUrl, info.getURL().toString()))) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    final String subPathUnderBranch = SVNPathUtil.getRelativePath(myBranchUrl, info.getURL().toString());

    final MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result = new MultiMap<SvnMergeInfoCache.MergeCheckResult, String>();
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

  private void checkPaths(final long number, final Collection<String> paths, final String branchPath, final String subPathUnderBranch,
                          final MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result) {
    final String myTrunkPathCorrespondingToLocalBranchPath = SVNPathUtil.append(myTrunkCorrected, subPathUnderBranch);
    for (String path : paths) {
      final String absoluteInTrunkPath = SVNPathUtil.append(myRepositoryRoot, path);
      if (! absoluteInTrunkPath.startsWith(myTrunkPathCorrespondingToLocalBranchPath)) {
        result.putValue(SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS, path);
        continue;
      }
      final String relativeToTrunkPath = absoluteInTrunkPath.substring(myTrunkPathCorrespondingToLocalBranchPath.length());
      final String localPathInBranch = new File(branchPath, relativeToTrunkPath).getAbsolutePath();
      
      final SvnMergeInfoCache.MergeCheckResult pathResult = checkPathGoingUp(number, -1, branchPath, localPathInBranch, path, true);
      result.putValue(pathResult, path);
    }
  }

  private SvnMergeInfoCache.MergeCheckResult goUp(final long revisionAsked, final long targetRevision, final String branchRootPath,
                                                              final String path, final String trunkUrl) {
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
      final SVNInfo svnInfo = getInfo(new File(branchRootPath));
      if (svnInfo == null || svnInfo.getRevision() == null || svnInfo.getURL() == null) {
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

    final SVNPropertyData mergeinfoProperty;
    try {
        mergeinfoProperty = myClient.doGetProperty(branchUrl, SVNProperty.MERGE_INFO, SVNRevision.UNDEFINED, SVNRevision.create(targetRevision));
    }
    catch (SVNException e) {
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
      final String absoluteTrunk = SVNPathUtil.append(myRepositoryRoot, newTrunkUrl);
      if ((1 >= newTrunkUrl.length()) || (myRepositoryRoot.length() >= newBranchUrl.toString().length()) ||
        (newBranchUrl.equals(absoluteTrunk))) {
        return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
      // go up
      return goUpInRepo(revisionAsked, targetRevision, newBranchUrl, newTrunkUrl);
    }
    // process
    return processMergeinfoProperty(keyString, revisionAsked, mergeinfoProperty.getValue(), trunkUrl, false);
  }

  private SVNInfo getInfo(final File pathFile) {
    try {
      return myClient.doInfo(pathFile, SVNRevision.WORKING);
    } catch (SVNException e) {
      //
    }
    return null;
  }

  private SvnMergeInfoCache.MergeCheckResult checkPathGoingUp(final long revisionAsked, final long targetRevision, final String branchRootPath,
                                                              final String path, final String trunkUrl, final boolean self) {
    final File pathFile = new File(path);

    if (targetRevision == -1) {
      // we didn't find existing item on the path jet
      // check whether we locally have path
      if (! pathFile.exists()) {
        // go into parent
        return goUp(revisionAsked, targetRevision, branchRootPath, path, trunkUrl);
      }
    }
    
    final SVNInfo svnInfo = getInfo(pathFile);
    if (svnInfo == null || svnInfo.getRevision() == null || svnInfo.getURL() == null) {
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
      final boolean merged = ((mergeInfo != null) && mergeInfo.contains(revisionAsked)) ||
                             ((selfInfo != null) && selfInfo.contains(revisionAsked));
      // take from self or first parent with info; do not go further 
      return SvnMergeInfoCache.MergeCheckResult.getInstance(merged);
    }

    final SVNPropertyData mergeinfoProperty;
    try {
      if (actualRevision == targetRevisionCorrected) {
        // look in WC
        mergeinfoProperty = myClient.doGetProperty(pathFile, SVNProperty.MERGE_INFO, SVNRevision.WORKING, SVNRevision.WORKING);
      } else {
        // in repo
        myMixedRevisionsFound = true;
        mergeinfoProperty = myClient.doGetProperty(svnInfo.getURL(), SVNProperty.MERGE_INFO, SVNRevision.UNDEFINED, SVNRevision.create(targetRevisionCorrected));
      }
    }
    catch (SVNException e) {
      LOG.info(e);
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    if (mergeinfoProperty == null) {
      // go up
      return goUp(revisionAsked, targetRevisionCorrected, branchRootPath, path, trunkUrl);
    }
    // process
    return processMergeinfoProperty(keyString, revisionAsked, mergeinfoProperty.getValue(), trunkUrl, self);
  }

  private SvnMergeInfoCache.MergeCheckResult processMergeinfoProperty(final String pathWithRevisionNumber, final long revisionAsked,
                                                                      final SVNPropertyValue value, final String trunkRelativeUrl,
                                                                      final boolean self) {
    final String valueAsString = value.toString().trim();

    // empty mergeinfo
    if (valueAsString.length() == 0) {
      myPathMergedMap.put(pathWithRevisionNumber, Collections.<Long>emptySet());
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    final Map<String, SVNMergeRangeList> map;
    try {
      map = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(value.getString()), null);
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

  public boolean isMixedRevisionsFound() {
    return myMixedRevisionsFound;
  }

  // if nothing, maybe all not merged or merged: here only partly not merged
  public Collection<String> getNotMergedPaths(final long number) {
    return myPartlyMerged.get(number);
  }
}
