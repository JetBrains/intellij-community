/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.Status;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    final List<VirtualFile> basicVfRoots = ObjectsConvertor.convert(myResult.myTopRoots, new Convertor<RootUrlInfo, VirtualFile>() {
      public VirtualFile convert(final RootUrlInfo real) {
        return real.getVirtualFile();
      }
    });

    final ChangeListManager clManager = ChangeListManager.getInstance(myVcs.getProject());

    if (clearState) {
      // clear what was reported before (could be for currently-not-existing roots)
      myNestedCopiesHolder.getAndClear();
    }
    clManager.invokeAfterUpdate(new Runnable() {
      public void run() {
        final List<RootUrlInfo> nestedRoots = new ArrayList<>();

        for (NestedCopyInfo info : myNestedCopiesHolder.getAndClear()) {
          if (NestedCopyType.external.equals(info.getType()) || NestedCopyType.switched.equals(info.getType())) {
            RootUrlInfo topRoot = findTopRoot(VfsUtilCore.virtualToIoFile(info.getFile()));

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
        myMapping.applyDetectionResult(myResult);

        callback.run();
      }
    }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, null, new Consumer<VcsDirtyScopeManager>() {
      public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
        if (clearState) {
          vcsDirtyScopeManager.filesDirty(null, basicVfRoots);
        }
      }
    }, null);
  }

  private void registerRootUrlFromNestedPoint(@NotNull NestedCopyInfo info, @NotNull List<RootUrlInfo> nestedRoots) {
    // TODO: Seems there could be issues if myTopRoots contains nested roots => RootUrlInfo.myRoot could be incorrect
    // TODO: (not nearest ancestor) for new RootUrlInfo
    RootUrlInfo topRoot = findAncestorTopRoot(info.getFile());

    if (topRoot != null) {
      SVNURL repoRoot = info.getRootURL();
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
      final File infoFile = VfsUtilCore.virtualToIoFile(info.getFile());
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
    return ContainerUtil.find(myResult.myTopRoots, new Condition<RootUrlInfo>() {
      @Override
      public boolean value(RootUrlInfo topRoot) {
        return FileUtil.filesEqual(topRoot.getIoFile(), file);
      }
    });
  }

  @Nullable
  private RootUrlInfo findAncestorTopRoot(@NotNull final VirtualFile file) {
    return ContainerUtil.find(myResult.myTopRoots, new Condition<RootUrlInfo>() {
      @Override
      public boolean value(RootUrlInfo topRoot) {
        return VfsUtilCore.isAncestor(topRoot.getVirtualFile(), file, true);
      }
    });
  }

  private static class RepositoryRoots {
    private final SvnVcs myVcs;
    private final Set<SVNURL> myRoots;

    private RepositoryRoots(final SvnVcs vcs) {
      myVcs = vcs;
      myRoots = new HashSet<>();
    }

    public void register(final SVNURL url) {
      myRoots.add(url);
    }

    public SVNURL ask(final SVNURL url, VirtualFile file) {
      for (SVNURL root : myRoots) {
        if (root.equals(SVNURLUtil.getCommonURLAncestor(root, url))) {
          return root;
        }
      }
      // TODO: Seems that RepositoryRoots class should be removed. And necessary repository root should be determined explicitly
      // TODO: using info command.
      final SVNURL newUrl = SvnUtil.getRepositoryRoot(myVcs, new File(file.getPath()));
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
