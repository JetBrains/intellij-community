// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.projectlevelman.MappingsToRoots;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD;
import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static org.jetbrains.idea.svn.SvnFormatSelector.findRootAndGetFormat;
import static org.jetbrains.idea.svn.SvnUtil.*;
import static org.jetbrains.idea.svn.SvnUtilKtKt.putWcDbFilesToVfs;

@State(name = "SvnFileUrlMappingImpl", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SvnFileUrlMappingImpl implements SvnFileUrlMapping, PersistentStateComponent<SvnMappingSavedPart>, Disposable {
  private static final Logger LOG = Logger.getInstance(SvnFileUrlMappingImpl.class);

  @NotNull private final Object myMonitor = new Object();
  // strictly: what real roots are under what vcs mappings
  @NotNull private final SvnMapping myMapping = new SvnMapping();
  // grouped; if there are several mappings one under another, will return the upmost
  @NotNull private final SvnMapping myMoreRealMapping = new SvnMapping();
  @NotNull private final List<RootUrlInfo> myErrorRoots = new ArrayList<>();
  @NotNull private final Project myProject;
  @NotNull private final NestedCopiesHolder myNestedCopiesHolder = new NestedCopiesHolder();
  private boolean myInitialized;
  private boolean myInitedReloaded;

  private final @NotNull MergingUpdateQueue myRefreshQueue =
    new MergingUpdateQueue("Refresh Working Copies", 100, true, null, this, null, POOLED_THREAD);

  @SuppressWarnings("UnusedDeclaration")
  private SvnFileUrlMappingImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
  }

  @Override
  @Nullable
  public Url getUrlForFile(@NotNull File file) {
    Url result = null;
    RootUrlInfo rootUrlInfo = getWcRootForFilePath(getFilePath(file));

    if (rootUrlInfo != null) {
      try {
        result = append(rootUrlInfo.getUrl(), getRelativePath(rootUrlInfo.getPath(), file.getAbsolutePath()));
      }
      catch (SvnBindException e) {
        LOG.info(e);
      }
    }

    return result;
  }

  @Override
  @Nullable
  public File getLocalPath(@NotNull Url url) {
    RootUrlInfo parentInfo = getWcRootForUrl(url);
    return parentInfo != null ? new File(parentInfo.getIoFile(), getRelativeUrl(parentInfo.getUrl(), url)) : null;
  }

  @Override
  @Nullable
  public RootUrlInfo getWcRootForFilePath(@NotNull FilePath path) {
    synchronized (myMonitor) {
      String root = myMoreRealMapping.getRootForPath(toSystemDependentName(path.toString()));

      return root != null ? myMoreRealMapping.byFile(root) : null;
    }
  }

  @Override
  @Nullable
  public RootUrlInfo getWcRootForUrl(@NotNull Url url) {
    synchronized (myMonitor) {
      RootUrlInfo result = null;
      Url rootUrl = find(myMoreRealMapping.getUrls(), parentRootUrl -> isAncestor(parentRootUrl, url));

      if (rootUrl != null) {
        result = myMoreRealMapping.byUrl(rootUrl);
        if (result == null) {
          LOG.info("Inconsistent maps for url:" + url + " found root url: " + rootUrl);
        }
      }

      return result;
    }
  }

  /**
   * Returns real working copies roots - if there is <Project Root> -> Subversion setting,
   * and there is one working copy, will return one root
   */
  @NotNull
  @Override
  public List<RootUrlInfo> getAllWcInfos() {
    synchronized (myMonitor) {
      return myMoreRealMapping.getAllCopies();
    }
  }

  @NotNull
  @Override
  public List<RootUrlInfo> getErrorRoots() {
    synchronized (myMonitor) {
      return new ArrayList<>(myErrorRoots);
    }
  }

  @Override
  @NotNull
  public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
    List<VirtualFile> cachedRoots;
    List<VirtualFile> lonelyRoots;
    synchronized (myMonitor) {
      cachedRoots = myMoreRealMapping.getUnderVcsRoots();
      lonelyRoots = myMoreRealMapping.getLonelyRoots();
    }

    myProject.getService(SvnCompatibilityChecker.class).checkAndNotify(lonelyRoots);
    return new ArrayList<>(cachedRoots.isEmpty() ? result : cachedRoots);
  }

  public void acceptNestedData(@NotNull Set<NestedCopyInfo> set) {
    myNestedCopiesHolder.add(set);
  }

  private boolean init() {
    synchronized (myMonitor) {
      boolean result = myInitialized;
      myInitialized = true;
      return result;
    }
  }

  public void scheduleRefresh() {
    myRefreshQueue.queue(DisposableUpdate.createDisposable(this, "refresh", () -> refresh()));
  }

  void scheduleRefresh(@NotNull Runnable callback) {
    myRefreshQueue.queue(DisposableUpdate.createDisposable(this, callback, () -> {
      try {
        refresh();
      }
      finally {
        callback.run();
      }
    }));
  }

  private void refresh() {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    VirtualFile[] roots = getNotFilteredRoots();
    SvnRootsDetector rootsDetector = new SvnRootsDetector(this, vcs, myNestedCopiesHolder);
    SvnRootsDetector.Result result = rootsDetector.detectCopyRoots(roots, init());

    if (result != null) {
      putWcDbFilesToVfs(result.getTopRoots());
      new NewRootsApplier(result).apply();
    }
  }

  private final class NewRootsApplier {

    @NotNull private final SvnRootsDetector.Result myResult;
    @NotNull private final SvnMapping myNewMapping = new SvnMapping();
    @NotNull private final SvnMapping myNewFilteredMapping = new SvnMapping();

    private NewRootsApplier(@NotNull SvnRootsDetector.Result result) {
      myResult = result;
    }

    public void apply() {
      myNewMapping.addAll(myResult.getTopRoots());
      myNewMapping.reportLonelyRoots(myResult.getLonelyRoots());
      myNewFilteredMapping.addAll(new UniqueRootsFilter().filter(myResult.getTopRoots()));

      runUpdateMappings();
    }

    private void runUpdateMappings() {
      // TODO: Not clear so far why read action is used here - may be because of ROOTS_RELOADED message sent?
      ReadAction.run(() -> {
        if (!myProject.isDisposed()) {
          boolean mappingsChanged = updateMappings();
          notifyRootsReloaded(mappingsChanged);
        }
      });
    }

    private boolean updateMappings() {
      boolean mappingsChanged;
      synchronized (myMonitor) {
        mappingsChanged = !myMoreRealMapping.equals(myNewFilteredMapping);
        mappingsChanged |= !myErrorRoots.equals(myResult.getErrorRoots());

        myMapping.copyFrom(myNewMapping);
        myMoreRealMapping.copyFrom(myNewFilteredMapping);
        myErrorRoots.clear();
        myErrorRoots.addAll(myResult.getErrorRoots());
      }
      return mappingsChanged;
    }

    private void notifyRootsReloaded(boolean mappingsChanged) {
      if (mappingsChanged || !myInitedReloaded) {
        myInitedReloaded = true;
        // all listeners are asynchronous
        BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.ROOTS_RELOADED).consume(true);
        BackgroundTaskUtil.syncPublisher(myProject, ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN).directoryMappingChanged();
      }
      else {
        BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.ROOTS_RELOADED).consume(false);
      }
    }
  }

  /**
   * Get raw roots from mappings, without applying our own {@link #convertRoots} and {@link #myMoreRealMapping}.
   */
  @Override
  public VirtualFile @NotNull [] getNotFilteredRoots() {
    SvnVcs svnVcs = SvnVcs.getInstance(myProject);
    List<VirtualFile> roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcsWithoutFiltering(svnVcs);
    return MappingsToRoots.filterAllowedRoots(myProject, roots, svnVcs);
  }

  @Override
  public boolean isEmpty() {
    synchronized (myMonitor) {
      return myMapping.isEmpty();
    }
  }

  @Override
  public SvnMappingSavedPart getState() {
    SvnMappingSavedPart result = new SvnMappingSavedPart();
    SvnMapping mapping = new SvnMapping();
    SvnMapping realMapping = new SvnMapping();

    synchronized (myMonitor) {
      mapping.copyFrom(myMapping);
      realMapping.copyFrom(myMoreRealMapping);
    }

    mapping.getAllCopies().forEach(result::add);
    realMapping.getAllCopies().forEach(result::addReal);

    return result;
  }

  @Override
  public void loadState(@NotNull final SvnMappingSavedPart state) {
    ProjectLevelVcsManager.getInstance(myProject).runAfterInitialization(
      () -> getApplication().executeOnPooledThread(() -> {
        SvnMapping mapping = new SvnMapping();
        SvnMapping realMapping = new SvnMapping();
        try {
          fillMapping(mapping, state.getMappingRoots());
          fillMapping(realMapping, state.getMoreRealMappingRoots());
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable t) {
          LOG.info(t);
          return;
        }

        synchronized (myMonitor) {
          myMapping.copyFrom(mapping);
          myMoreRealMapping.copyFrom(realMapping);
        }
      }));
  }

  private void fillMapping(@NotNull SvnMapping mapping, @NotNull List<SvnCopyRootSimple> list) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();

    for (SvnCopyRootSimple simple : list) {
      VirtualFile copyRoot = lfs.findFileByIoFile(new File(simple.myCopyRoot));
      VirtualFile vcsRoot = lfs.findFileByIoFile(new File(simple.myVcsRoot));

      if (copyRoot == null || vcsRoot == null) continue;

      SvnVcs vcs = SvnVcs.getInstance(myProject);
      Info info = vcs.getInfo(copyRoot);

      if (info != null && info.getRepositoryRootUrl() != null) {
        Node node = new Node(copyRoot, info.getUrl(), info.getRepositoryRootUrl());
        mapping.add(new RootUrlInfo(node, findRootAndGetFormat(info.getFile()), vcsRoot));
      }
    }
  }
}
