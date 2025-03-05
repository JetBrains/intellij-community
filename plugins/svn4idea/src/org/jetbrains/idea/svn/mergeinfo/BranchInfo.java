// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ExceptionUtil;
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
import java.util.*;

import static org.jetbrains.idea.svn.SvnUtil.*;

public class BranchInfo {

  private static final Logger LOG = Logger.getInstance(BranchInfo.class);
  // repo path in branch in format path@revision -> merged revisions
  private final @NotNull Map<String, Set<Long>> myPathMergedMap;
  private final @NotNull Map<String, Set<Long>> myNonInheritablePathMergedMap;

  private boolean myMixedRevisionsFound;

  // revision in trunk -> whether merged into branch
  private final @NotNull Map<Long, MergeCheckResult> myAlreadyCalculatedMap;
  private final @NotNull Object myCalculatedLock = new Object();

  private final @NotNull WCInfoWithBranches myInfo;
  private final @NotNull WCInfoWithBranches.Branch myBranch;
  private final @NotNull SvnVcs myVcs;

  private SvnMergeInfoCache.CopyRevison myCopyRevison;
  private final @NotNull MultiMap<Long, String> myPartlyMerged;

  public BranchInfo(@NotNull SvnVcs vcs, @NotNull WCInfoWithBranches info, @NotNull WCInfoWithBranches.Branch branch) {
    myVcs = vcs;
    myInfo = info;
    myBranch = branch;

    myPathMergedMap = new HashMap<>();
    myPartlyMerged = MultiMap.create();
    myNonInheritablePathMergedMap = new HashMap<>();

    myAlreadyCalculatedMap = new HashMap<>();
  }

  private long calculateCopyRevision(final String branchPath) {
    if (myCopyRevison != null && Objects.equals(myCopyRevison.getPath(), branchPath)) {
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

  public @NotNull MergeInfoCached getCached() {
    synchronized (myCalculatedLock) {
      long revision = myCopyRevison != null ? myCopyRevison.getRevision() : -1;

      // TODO: NEW MAP WILL ALSO BE CREATED IN MergeInfoCached constructor
      return new MergeInfoCached(Collections.unmodifiableMap(myAlreadyCalculatedMap), revision);
    }
  }

  // branch path - is local working copy path
  public @NotNull MergeCheckResult checkList(final @NotNull SvnChangeList list, final String branchPath) {
    synchronized (myCalculatedLock) {
      MergeCheckResult result;
      final long revision = calculateCopyRevision(branchPath);
      if (revision != -1 && revision >= list.getNumber()) {
        result = MergeCheckResult.COMMON;
      }
      else {
        result = myAlreadyCalculatedMap.computeIfAbsent(list.getNumber(), __ -> checkAlive(list, branchPath));
      }
      return result;
    }
  }

  private @NotNull MergeCheckResult checkAlive(@NotNull SvnChangeList list, @NotNull String branchPath) {
    final Info info = myVcs.getInfo(new File(branchPath));
    if (info == null || info.getUrl() == null || !isAncestor(myBranch.getUrl(), info.getUrl())) {
      return MergeCheckResult.NOT_MERGED;
    }

    MultiMap<MergeCheckResult, String> result = checkPaths(list, branchPath, info.getUrl());

    if (result.containsKey(MergeCheckResult.NOT_EXISTS)) {
      return MergeCheckResult.NOT_EXISTS;
    }
    if (result.containsKey(MergeCheckResult.NOT_MERGED)) {
      myPartlyMerged.put(list.getNumber(), result.get(MergeCheckResult.NOT_MERGED));
      return MergeCheckResult.NOT_MERGED;
    }
    return MergeCheckResult.MERGED;
  }

  private @NotNull MultiMap<MergeCheckResult, String> checkPaths(@NotNull SvnChangeList list,
                                                                 @NotNull String branchPath,
                                                                 @NotNull Url underBranchUrl) {
    MultiMap<MergeCheckResult, String> result = MultiMap.create();
    String subPathUnderBranch = getRelativeUrl(myBranch.getUrl(), underBranchUrl);
    Url myTrunkUrlCorrespondingToLocalBranchPath = appendPath(myInfo.getCurrentBranch().getUrl(), subPathUnderBranch);

    for (String path : list.getAffectedPaths()) {
      Url url = appendPath(myInfo.getRepoUrl(), path);
      MergeCheckResult mergeCheckResult;

      if (!isAncestor(myTrunkUrlCorrespondingToLocalBranchPath, url)) {
        mergeCheckResult = MergeCheckResult.NOT_EXISTS;
      }
      else {
        String relativeToTrunkPath = getRelativeUrl(myTrunkUrlCorrespondingToLocalBranchPath, url);
        String localPathInBranch = new File(branchPath, relativeToTrunkPath).getAbsolutePath();

        try {
          mergeCheckResult = checkPathGoingUp(list.getNumber(), -1, branchPath, localPathInBranch, path, true);
        }
        catch (VcsException e) {
          LOG.info(e);
          mergeCheckResult = MergeCheckResult.NOT_MERGED;
        }
      }

      result.putValue(mergeCheckResult, path);

      // Do not check other paths if NOT_EXISTS result detected as in this case resulting status for whole change list will also be
      // NOT_EXISTS. And currently we're only interested in not merged paths for change lists with NOT_MERGED status.
      if (MergeCheckResult.NOT_EXISTS.equals(mergeCheckResult)) {
        break;
      }
    }

    return result;
  }

  private @NotNull MergeCheckResult goUp(final long revisionAsked,
                                         final long targetRevision,
                                         final String branchRootPath,
                                         final String path,
                                         @NotNull String trunkUrl) throws VcsException {
    MergeCheckResult result;
    String newTrunkUrl = Url.removeTail(trunkUrl).trim();

    if (newTrunkUrl.isEmpty() || "/".equals(newTrunkUrl)) {
      result = MergeCheckResult.NOT_MERGED;
    }
    else {
      String newPath = new File(path).getParent();
      if (newPath.length() < branchRootPath.length()) {
        // we are higher than WC root -> go into repo only
        if (targetRevision == -1) {
          // no paths in local copy
          result = MergeCheckResult.NOT_EXISTS;
        }
        else {
          Info svnInfo = myVcs.getInfo(new File(branchRootPath));
          result = svnInfo == null || svnInfo.getUrl() == null
                   ? MergeCheckResult.NOT_MERGED
                   : goUpInRepo(revisionAsked, targetRevision, removePathTail(svnInfo.getUrl()), newTrunkUrl);
        }
      }
      else {
        result = checkPathGoingUp(revisionAsked, targetRevision, branchRootPath, newPath, newTrunkUrl, false);
      }
    }

    return result;
  }

  private @NotNull MergeCheckResult goUpInRepo(final long revisionAsked,
                                               final long targetRevision,
                                               final Url branchUrl,
                                               final String trunkUrl) throws VcsException {
    MergeCheckResult result;
    Set<Long> mergeInfo = myPathMergedMap.get(branchUrl.toString() + "@" + targetRevision);

    if (mergeInfo != null) {
      // take from self or first parent with info; do not go further
      result = MergeCheckResult.getInstance(mergeInfo.contains(revisionAsked));
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
                 ? MergeCheckResult.NOT_MERGED
                 : goUpInRepo(revisionAsked, targetRevision, newBranchUrl, newTrunkUrl);
      }
      else {
        result = processMergeinfoProperty(branchUrl.toString() + "@" + targetRevision, revisionAsked, mergeinfoProperty, trunkUrl, false);
      }
    }

    return result;
  }

  private @NotNull MergeCheckResult checkPathGoingUp(final long revisionAsked,
                                                     final long targetRevision,
                                                     @NotNull String branchRootPath,
                                                     @NotNull String path,
                                                     final String trunkUrl,
                                                     final boolean self) throws VcsException {
    MergeCheckResult result;
    final File pathFile = new File(path);

    // we didn't find existing item on the path jet
    // check whether we locally have path
    if (targetRevision == -1 && !pathFile.exists()) {
      result = goUp(revisionAsked, targetRevision, branchRootPath, path, trunkUrl);
    }
    else {
      final Info svnInfo = myVcs.getInfo(pathFile);
      if (svnInfo == null || svnInfo.getUrl() == null) {
        LOG.info("Svninfo for " + pathFile + " is null or not full.");
        result = MergeCheckResult.NOT_MERGED;
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
          result = MergeCheckResult.getInstance(merged);
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
            target = Target.on(svnInfo.getUrl());
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

  private @NotNull MergeCheckResult processMergeinfoProperty(final String pathWithRevisionNumber,
                                                             final long revisionAsked,
                                                             @NotNull PropertyValue value,
                                                             final String trunkRelativeUrl,
                                                             final boolean self) throws SvnBindException {
    MergeCheckResult result;
    Map<String, MergeRangeList> mergedPathsMap = MergeRangeList.parseMergeInfo(value.toString());
    String mergedPathAffectingTrunkUrl = ContainerUtil.find(mergedPathsMap.keySet(), path -> trunkRelativeUrl.startsWith(path));

    if (mergedPathAffectingTrunkUrl != null) {
      MergeRangeList mergeRangeList = mergedPathsMap.get(mergedPathAffectingTrunkUrl);

      fillMergedRevisions(pathWithRevisionNumber, mergeRangeList);

      boolean isAskedRevisionMerged =
        ContainerUtil.or(mergeRangeList.getRanges(), range -> range.contains(revisionAsked) && (range.isInheritable() || self));

      result = MergeCheckResult.getInstance(isAskedRevisionMerged);
    }
    else {
      myPathMergedMap.put(pathWithRevisionNumber, Collections.emptySet());
      result = MergeCheckResult.NOT_MERGED;
    }

    return result;
  }

  private void fillMergedRevisions(String pathWithRevisionNumber, @NotNull MergeRangeList mergeRangeList) {
    Set<Long> revisions = new HashSet<>();
    Set<Long> nonInheritableRevisions = new HashSet<>();

    for (MergeRange range : mergeRangeList.getRanges()) {
      // TODO: Seems there is no much sense in converting merge range to list of revisions - we need just implement smart search
      // TODO: of revision in sorted list of ranges
      ContainerUtil.addAll(range.isInheritable() ? revisions : nonInheritableRevisions, range.getRevisions());
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
  public @NotNull Collection<String> getNotMergedPaths(final long number) {
    return myPartlyMerged.get(number);
  }

  private static @NotNull Url appendPath(@NotNull Url url, @NotNull String path) {
    try {
      return url.appendPath(path, false);
    }
    catch (SvnBindException e) {
      ExceptionUtil.rethrow(e);
      return null;
    }
  }
}
