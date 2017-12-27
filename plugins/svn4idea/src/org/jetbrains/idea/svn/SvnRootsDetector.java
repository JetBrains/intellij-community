// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.status.Status;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

/**
* @author Konstantin Kolosovsky.
*/
public class SvnRootsDetector {

  private static final Logger LOG = Logger.getInstance(SvnRootsDetector.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final SvnFileUrlMappingImpl myMapping;
  @NotNull private final Result myResult;
  @NotNull private final RepositoryRoots myRepositoryRoots;
  @NotNull private final NestedCopiesHolder myNestedCopiesHolder;

  public SvnRootsDetector(@NotNull final SvnVcs vcs,
                          @NotNull final SvnFileUrlMappingImpl mapping,
                          @NotNull final NestedCopiesHolder holder) {
    myVcs = vcs;
    myMapping = mapping;
    myResult = new Result();
    myNestedCopiesHolder = holder;
    myRepositoryRoots = new RepositoryRoots(myVcs);
  }

  public void detectCopyRoots(final VirtualFile[] roots, final boolean clearState, Runnable callback) {
    for (final VirtualFile vcsRoot : roots) {
      List<Node> foundRoots = new ForNestedRootChecker(myVcs).getAllNestedWorkingCopies(vcsRoot);

      registerLonelyRoots(vcsRoot, foundRoots);
      registerTopRoots(vcsRoot, foundRoots);
    }

    addNestedRoots(clearState, callback);
  }

  private void registerLonelyRoots(VirtualFile vcsRoot, List<Node> foundRoots) {
    if (foundRoots.isEmpty()) {
      myResult.myLonelyRoots.add(vcsRoot);
    }
  }

  private void registerTopRoots(@NotNull VirtualFile vcsRoot, @NotNull List<Node> foundRoots) {
    // filter out bad(?) items
    for (Node foundRoot : foundRoots) {
      RootUrlInfo root = new RootUrlInfo(foundRoot, SvnFormatSelector.findRootAndGetFormat(foundRoot.getIoFile()), vcsRoot);

      if (!foundRoot.hasError()) {
        myRepositoryRoots.register(foundRoot.getRepositoryRootUrl());
        myResult.myTopRoots.add(root);
      } else {
        myResult.myErrorRoots.add(root);
      }
    }
  }

  private void addNestedRoots(final boolean clearState, final Runnable callback) {
    List<VirtualFile> basicVfRoots = map(myResult.myTopRoots, RootUrlInfo::getVirtualFile);
    final ChangeListManager clManager = ChangeListManager.getInstance(myVcs.getProject());

    if (clearState) {
      // clear what was reported before (could be for currently-not-existing roots)
      myNestedCopiesHolder.getAndClear();
    }
    clManager.invokeAfterUpdate(() -> {
      final List<RootUrlInfo> nestedRoots = new ArrayList<>();

      for (NestedCopyInfo info : myNestedCopiesHolder.getAndClear()) {
        if (NestedCopyType.external.equals(info.getType()) || NestedCopyType.switched.equals(info.getType())) {
          RootUrlInfo topRoot = findTopRoot(virtualToIoFile(info.getFile()));

          if (topRoot != null) {
            // TODO: Seems that type is not set in ForNestedRootChecker as we could not determine it for sure. Probably, for the case
            // TODO: (or some other cases) when vcs root from settings belongs is in externals of some other working copy upper
            // TODO: the tree (I did not check this). Leave this setter for now.
            topRoot.setType(info.getType());
            continue;
          }
          if (!refreshPointInfo(info)) {
            continue;
          }
        }
        registerRootUrlFromNestedPoint(info, nestedRoots);
      }

      myResult.myTopRoots.addAll(nestedRoots);
      putWcDbFilesToVfs(myResult.myTopRoots);
      myMapping.applyDetectionResult(myResult);

      callback.run();
    }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, null, vcsDirtyScopeManager -> {
      if (clearState) {
        vcsDirtyScopeManager.filesDirty(null, basicVfRoots);
      }
    }, null);
  }

  private static void putWcDbFilesToVfs(@NotNull Collection<RootUrlInfo> infos) {
    if (!SvnVcs.ourListenToWcDb) return;

    List<File> wcDbFiles = infos.stream()
      .filter(info -> info.getFormat().isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN))
      .filter(info -> !NestedCopyType.switched.equals(info.getType()))
      .map(RootUrlInfo::getIoFile)
      .map(SvnUtil::getWcDb)
      .collect(toList());

    LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles);
  }

  private void registerRootUrlFromNestedPoint(@NotNull NestedCopyInfo info, @NotNull List<RootUrlInfo> nestedRoots) {
    // TODO: Seems there could be issues if myTopRoots contains nested roots => RootUrlInfo.myRoot could be incorrect
    // TODO: (not nearest ancestor) for new RootUrlInfo
    RootUrlInfo topRoot = findAncestorTopRoot(info.getFile());

    if (topRoot != null) {
      Url repoRoot = info.getRootURL();
      repoRoot = repoRoot == null ? myRepositoryRoots.ask(info.getUrl(), info.getFile()) : repoRoot;
      if (repoRoot != null) {
        Node node = new Node(info.getFile(), info.getUrl(), repoRoot);
        nestedRoots.add(new RootUrlInfo(node, info.getFormat(), topRoot.getRoot(), info.getType()));
      }
    }
  }

  private boolean refreshPointInfo(@NotNull NestedCopyInfo info) {
    // TODO: Here we refresh url, repository url, format because they are not set for some NestedCopies in NestedCopiesBuilder.
    // TODO: For example they are not set for externals. Probably this logic could be moved to NestedCopiesBuilder instead.
    boolean refreshed = false;

    // TODO: No checked exceptions are thrown - remove catch/LOG.error/rethrow to fix real cause if any
    try {
      final File infoFile = virtualToIoFile(info.getFile());
      final Status svnStatus = SvnUtil.getStatus(myVcs, infoFile);

      if (svnStatus != null && svnStatus.getURL() != null) {
        info.setUrl(svnStatus.getURL());
        info.setFormat(myVcs.getWorkingCopyFormat(infoFile, false));
        if (svnStatus.getRepositoryRootURL() != null) {
          info.setRootURL(svnStatus.getRepositoryRootURL());
        }
        refreshed = true;
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }

    return refreshed;
  }

  @Nullable
  private RootUrlInfo findTopRoot(@NotNull final File file) {
    return ContainerUtil.find(myResult.myTopRoots, topRoot -> FileUtil.filesEqual(topRoot.getIoFile(), file));
  }

  @Nullable
  private RootUrlInfo findAncestorTopRoot(@NotNull final VirtualFile file) {
    return ContainerUtil.find(myResult.myTopRoots, topRoot -> VfsUtilCore.isAncestor(topRoot.getVirtualFile(), file, true));
  }

  private static class RepositoryRoots {
    private final SvnVcs myVcs;
    private final Set<Url> myRoots;

    private RepositoryRoots(final SvnVcs vcs) {
      myVcs = vcs;
      myRoots = new HashSet<>();
    }

    public void register(final Url url) {
      myRoots.add(url);
    }

    public Url ask(final Url url, VirtualFile file) {
      for (Url root : myRoots) {
        if (isAncestor(root, url)) {
          return root;
        }
      }
      // TODO: Seems that RepositoryRoots class should be removed. And necessary repository root should be determined explicitly
      // TODO: using info command.
      final Url newUrl = SvnUtil.getRepositoryRoot(myVcs, virtualToIoFile(file));
      if (newUrl != null) {
        myRoots.add(newUrl);
        return newUrl;
      }
      return null;
    }
  }

  public static class Result {

    @NotNull private final List<VirtualFile> myLonelyRoots;
    @NotNull private final List<RootUrlInfo> myTopRoots;
    @NotNull private final List<RootUrlInfo> myErrorRoots;

    public Result() {
      myTopRoots = new ArrayList<>();
      myErrorRoots = new ArrayList<>();
      myLonelyRoots = new ArrayList<>();
    }

    @NotNull
    public List<VirtualFile> getLonelyRoots() {
      return myLonelyRoots;
    }

    @NotNull
    public List<RootUrlInfo> getTopRoots() {
      return myTopRoots;
    }

    @NotNull
    public List<RootUrlInfo> getErrorRoots() {
      return myErrorRoots;
    }
  }
}
