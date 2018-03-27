// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.addAll;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;

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
    myCopyRevison =
      new SvnMergeInfoCache.CopyRevison(myVcs, branchPath, myInfo.getRootInfo().getRepositoryUrl(), myBranch.getUrl(), myInfo.getUrl());
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

  // branch path - is local working copy path
  @NotNull
  public SvnMergeInfoCache.MergeCheckResult checkList(@NotNull final SvnChangeList list, final String branchPath) {
    synchronized (myCalculatedLock) {
      SvnMergeInfoCache.MergeCheckResult result;
      final long revision = calculateCopyRevision(branchPath);
      if (revision != -1 && revision >= list.getNumber()) {
        result = SvnMergeInfoCache.MergeCheckResult.COMMON;
      }
      else {
        result = ContainerUtil.getOrCreate(myAlreadyCalculatedMap, list.getNumber(),
                                           (Factory<SvnMergeInfoCache.MergeCheckResult>)() -> checkAlive(list, branchPath));
      }
      return result;
    }
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult checkAlive(@NotNull SvnChangeList list, @NotNull String branchPath) {
    final Info info = myVcs.getInfo(new File(branchPath));
    if (info == null || info.getURL() == null || !Url.isAncestor(myBranch.getUrl(), info.getURL().toString())) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    final String subPathUnderBranch = Url.getRelative(myBranch.getUrl(), info.getURL().toString());
    MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result = checkPaths(list, branchPath, subPathUnderBranch);

    if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS)) {
      return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    }
    if (result.containsKey(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)) {
      myPartlyMerged.put(list.getNumber(), result.get(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED));
      return SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    return SvnMergeInfoCache.MergeCheckResult.MERGED;
  }

  @NotNull
  private MultiMap<SvnMergeInfoCache.MergeCheckResult, String> checkPaths(@NotNull SvnChangeList list,
                                                                          @NotNull String branchPath,
                                                                          final String subPathUnderBranch) {
    MultiMap<SvnMergeInfoCache.MergeCheckResult, String> result = MultiMap.create();
    String myTrunkPathCorrespondingToLocalBranchPath = Url.append(myInfo.getCurrentBranch().getUrl(), subPathUnderBranch);

    for (String path : list.getAffectedPaths()) {
      String absoluteInTrunkPath = Url.append(myInfo.getRepoUrl().toString(), path);
      SvnMergeInfoCache.MergeCheckResult mergeCheckResult;

      if (!absoluteInTrunkPath.startsWith(myTrunkPathCorrespondingToLocalBranchPath)) {
        mergeCheckResult = SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
      }
      else {
        String relativeToTrunkPath = absoluteInTrunkPath.substring(myTrunkPathCorrespondingToLocalBranchPath.length());
        String localPathInBranch = new File(branchPath, relativeToTrunkPath).getAbsolutePath();

        try {
          mergeCheckResult = checkPathGoingUp(list.getNumber(), -1, branchPath, localPathInBranch, path, true);
        }
        catch (VcsException e) {
          LOG.info(e);
          mergeCheckResult = SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
        }
      }

      result.putValue(mergeCheckResult, path);

      // Do not check other paths if NOT_EXISTS result detected as in this case resulting status for whole change list will also be
      // NOT_EXISTS. And currently we're only interested in not merged paths for change lists with NOT_MERGED status.
      if (SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS.equals(mergeCheckResult)) {
        break;
      }
    }

    return result;
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult goUp(final long revisionAsked,
                                                  final long targetRevision,
                                                  final String branchRootPath,
                                                  final String path,
                                                  @NotNull String trunkUrl) throws VcsException {
    SvnMergeInfoCache.MergeCheckResult result;
    String newTrunkUrl = Url.removeTail(trunkUrl).trim();

    if (newTrunkUrl.length() == 0 || "/".equals(newTrunkUrl)) {
      result = SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }
    else {
      String newPath = new File(path).getParent();
      if (newPath.length() < branchRootPath.length()) {
        // we are higher than WC root -> go into repo only
        if (targetRevision == -1) {
          // no paths in local copy
          result = SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
        }
        else {
          Info svnInfo = myVcs.getInfo(new File(branchRootPath));
          result = svnInfo == null || svnInfo.getURL() == null
                   ? SvnMergeInfoCache.MergeCheckResult.NOT_MERGED
                   : goUpInRepo(revisionAsked, targetRevision, removePathTail(svnInfo.getURL()), newTrunkUrl);
        }
      }
      else {
        result = checkPathGoingUp(revisionAsked, targetRevision, branchRootPath, newPath, newTrunkUrl, false);
      }
    }

    return result;
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult goUpInRepo(final long revisionAsked,
                                                        final long targetRevision,
                                                        final Url branchUrl,
                                                        final String trunkUrl) throws VcsException {
    SvnMergeInfoCache.MergeCheckResult result;
    Set<Long> mergeInfo = myPathMergedMap.get(branchUrl.toString() + "@" + targetRevision);

    if (mergeInfo != null) {
      // take from self or first parent with info; do not go further
      result = SvnMergeInfoCache.MergeCheckResult.getInstance(mergeInfo.contains(revisionAsked));
    }
    else {
      Target target = Target.on(branchUrl);
      PropertyValue mergeinfoProperty = myVcs.getFactory(target).createPropertyClient()
        .getProperty(target, SvnPropertyKeys.MERGE_INFO, false, Revision.of(targetRevision));

      if (mergeinfoProperty == null) {
        final String newTrunkUrl = Url.removeTail(trunkUrl).trim();
        final Url newBranchUrl = removePathTail(branchUrl);
        Url absoluteTrunk = append(myInfo.getRepoUrl(), newTrunkUrl);

        result = newTrunkUrl.length() <= 1 ||
                 newBranchUrl.toString().length() <= myInfo.getRepoUrl().toString().length() ||
                 newBranchUrl.equals(absoluteTrunk)
                 ? SvnMergeInfoCache.MergeCheckResult.NOT_MERGED
                 : goUpInRepo(revisionAsked, targetRevision, newBranchUrl, newTrunkUrl);
      }
      else {
        result = processMergeinfoProperty(branchUrl.toString() + "@" + targetRevision, revisionAsked, mergeinfoProperty, trunkUrl, false);
      }
    }

    return result;
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult checkPathGoingUp(final long revisionAsked,
                                                              final long targetRevision,
                                                              @NotNull String branchRootPath,
                                                              @NotNull String path,
                                                              final String trunkUrl,
                                                              final boolean self) throws VcsException {
    SvnMergeInfoCache.MergeCheckResult result;
    final File pathFile = new File(path);

    // we didn't find existing item on the path jet
    // check whether we locally have path
    if (targetRevision == -1 && !pathFile.exists()) {
      result = goUp(revisionAsked, targetRevision, branchRootPath, path, trunkUrl);
    }
    else {
      final Info svnInfo = myVcs.getInfo(pathFile);
      if (svnInfo == null || svnInfo.getURL() == null) {
        LOG.info("Svninfo for " + pathFile + " is null or not full.");
        result = SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
      }
      else {
        final long actualRevision = svnInfo.getRevision().getNumber();
        final long targetRevisionCorrected = (targetRevision == -1) ? actualRevision : targetRevision;

        // here we know local URL and revision

        // check existing info
        final String keyString = path + "@" + targetRevisionCorrected;
        final Set<Long> selfInfo = self ? myNonInheritablePathMergedMap.get(keyString) : null;
        final Set<Long> mergeInfo = myPathMergedMap.get(keyString);
        if (mergeInfo != null || selfInfo != null) {
          boolean merged = mergeInfo != null && mergeInfo.contains(revisionAsked) || selfInfo != null && selfInfo.contains(revisionAsked);
          // take from self or first parent with info; do not go further
          result = SvnMergeInfoCache.MergeCheckResult.getInstance(merged);
        }
        else {
          if (actualRevision != targetRevisionCorrected) {
            myMixedRevisionsFound = true;
          }

          Target target;
          Revision revision;
          if (actualRevision == targetRevisionCorrected) {
            // look in WC
            target = Target.on(pathFile, Revision.WORKING);
            revision = Revision.WORKING;
          }
          else {
            // in repo
            target = Target.on(svnInfo.getURL());
            revision = Revision.of(targetRevisionCorrected);
          }

          PropertyValue mergeinfoProperty =
            myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, revision);

          result = mergeinfoProperty == null
                   ? goUp(revisionAsked, targetRevisionCorrected, branchRootPath, path, trunkUrl)
                   : processMergeinfoProperty(keyString, revisionAsked, mergeinfoProperty, trunkUrl, self);
        }
      }
    }

    return result;
  }

  @NotNull
  private SvnMergeInfoCache.MergeCheckResult processMergeinfoProperty(final String pathWithRevisionNumber,
                                                                      final long revisionAsked,
                                                                      @NotNull PropertyValue value,
                                                                      final String trunkRelativeUrl,
                                                                      final boolean self) throws SvnBindException {
    SvnMergeInfoCache.MergeCheckResult result;
    Map<String, MergeRangeList> mergedPathsMap = MergeRangeList.parseMergeInfo(value.toString());
    String mergedPathAffectingTrunkUrl = ContainerUtil.find(mergedPathsMap.keySet(), path -> trunkRelativeUrl.startsWith(path));

    if (mergedPathAffectingTrunkUrl != null) {
      MergeRangeList mergeRangeList = mergedPathsMap.get(mergedPathAffectingTrunkUrl);

      fillMergedRevisions(pathWithRevisionNumber, mergeRangeList);

      boolean isAskedRevisionMerged =
        ContainerUtil.or(mergeRangeList.getRanges(), range -> range.contains(revisionAsked) && (range.isInheritable() || self));

      result = SvnMergeInfoCache.MergeCheckResult.getInstance(isAskedRevisionMerged);
    }
    else {
      myPathMergedMap.put(pathWithRevisionNumber, Collections.emptySet());
      result = SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
    }

    return result;
  }

  private void fillMergedRevisions(String pathWithRevisionNumber, @NotNull MergeRangeList mergeRangeList) {
    Set<Long> revisions = ContainerUtil.newHashSet();
    Set<Long> nonInheritableRevisions = ContainerUtil.newHashSet();

    for (MergeRange range : mergeRangeList.getRanges()) {
      // TODO: Seems there is no much sense in converting merge range to list of revisions - we need just implement smart search
      // TODO: of revision in sorted list of ranges
      addAll(range.isInheritable() ? revisions : nonInheritableRevisions, range.getRevisions());
    }

    myPathMergedMap.put(pathWithRevisionNumber, revisions);
    if (!nonInheritableRevisions.isEmpty()) {
      myNonInheritablePathMergedMap.put(pathWithRevisionNumber, nonInheritableRevisions);
    }
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
