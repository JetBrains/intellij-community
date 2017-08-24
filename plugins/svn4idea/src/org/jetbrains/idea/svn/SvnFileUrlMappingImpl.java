/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.List;
import java.util.Set;

@State(name = "SvnFileUrlMappingImpl", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SvnFileUrlMappingImpl implements SvnFileUrlMapping, PersistentStateComponent<SvnMappingSavedPart> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileUrlMappingImpl");

  private final SvnCompatibilityChecker myChecker;
  private final Object myMonitor = new Object();
  // strictly: what real roots are under what vcs mappings
  private final SvnMapping myMapping;
  // grouped; if there are several mappings one under another, will return the upmost
  private final SvnMapping myMoreRealMapping;
  private final List<RootUrlInfo> myErrorRoots;
  @NotNull private final MyRootsHelper myRootsHelper;
  private final Project myProject;
  private final NestedCopiesHolder myNestedCopiesHolder;
  private boolean myInitialized;
  private boolean myInitedReloaded;

  private static class MyRootsHelper {
    @NotNull private final static ThreadLocal<Boolean> ourInProgress = ThreadLocal.withInitial(() -> Boolean.FALSE);
    @NotNull private final Project myProject;
    @NotNull private final ProjectLevelVcsManager myVcsManager;

    private MyRootsHelper(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager) {
      myProject = project;
      myVcsManager = vcsManager;
    }

    @NotNull
    public VirtualFile[] execute() {
      try {
        ourInProgress.set(Boolean.TRUE);
        return myVcsManager.getRootsUnderVcs(SvnVcs.getInstance(myProject));
      }
      finally {
        ourInProgress.set(Boolean.FALSE);
      }
    }

    public static boolean isInProgress() {
      return ourInProgress.get();
    }
  }

  @NotNull
  public static SvnFileUrlMappingImpl getInstance(@NotNull Project project) {
    return (SvnFileUrlMappingImpl)ServiceManager.getService(project, SvnFileUrlMapping.class);
  }

  @SuppressWarnings("UnusedDeclaration")
  private SvnFileUrlMappingImpl(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myMapping = new SvnMapping();
    myMoreRealMapping = new SvnMapping();
    myErrorRoots = ContainerUtil.newArrayList();
    myRootsHelper = new MyRootsHelper(project, vcsManager);
    myChecker = new SvnCompatibilityChecker(project);
    myNestedCopiesHolder = new NestedCopiesHolder();
  }

  @Override
  @Nullable
  public SVNURL getUrlForFile(final File file) {
    final RootUrlInfo rootUrlInfo = getWcRootForFilePath(file);
    if (rootUrlInfo == null) {
      return null;
    }

    final String absolutePath = file.getAbsolutePath();
    final String rootAbsPath = rootUrlInfo.getIoFile().getAbsolutePath();
    if (absolutePath.length() < rootAbsPath.length()) {
      // remove last separator from etalon name
      if (absolutePath.equals(rootAbsPath.substring(0, rootAbsPath.length() - 1))) {
        return rootUrlInfo.getAbsoluteUrlAsUrl();
      }
      return null;
    }
    final String relativePath = absolutePath.substring(rootAbsPath.length());
    try {
      return rootUrlInfo.getAbsoluteUrlAsUrl().appendPath(FileUtil.toSystemIndependentName(relativePath), true);
    }
    catch (SVNException e) {
      LOG.info(e);
      return null;
    }
  }

  @Override
  @Nullable
  public File getLocalPath(@NotNull String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url);
      if (rootUrl == null) {
        return null;
      }
      final RootUrlInfo parentInfo = myMoreRealMapping.byUrl(rootUrl);
      if (parentInfo == null) {
        return null;
      }

      return new File(parentInfo.getIoFile(), url.substring(rootUrl.length()));
    }
  }

  @Override
  @Nullable
  public RootUrlInfo getWcRootForFilePath(final File file) {
    synchronized (myMonitor) {
      final String root = getRootForPath(file);
      if (root == null) {
        return null;
      }

      return myMoreRealMapping.byFile(root);
    }
  }

  @Override
  @Nullable
  public RootUrlInfo getWcRootForUrl(final String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url);
      if (rootUrl == null) {
        return null;
      }

      final RootUrlInfo result = myMoreRealMapping.byUrl(rootUrl);
      if (result == null) {
        LOG.info("Inconsistent maps for url:" + url + " found root url: " + rootUrl);
        return null;
      }
      return result;
    }
  }

  /**
   * Returns real working copies roots - if there is <Project Root> -> Subversion setting,
   * and there is one working copy, will return one root
   */
  @Override
  public List<RootUrlInfo> getAllWcInfos() {
    synchronized (myMonitor) {
      // a copy is created inside
      return myMoreRealMapping.getAllCopies();
    }
  }

  @Override
  public List<RootUrlInfo> getErrorRoots() {
    synchronized (myMonitor) {
      return ContainerUtil.newArrayList(myErrorRoots);
    }
  }

  @Override
  @NotNull
  public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
    if (MyRootsHelper.isInProgress()) return ContainerUtil.newArrayList(result);

    synchronized (myMonitor) {
      final List<VirtualFile> cachedRoots = myMoreRealMapping.getUnderVcsRoots();
      final List<VirtualFile> lonelyRoots = myMoreRealMapping.getLonelyRoots();
      if (! lonelyRoots.isEmpty()) {
        myChecker.reportNoRoots(lonelyRoots);
      }

      return ContainerUtil.newArrayList(cachedRoots.isEmpty() ? result : cachedRoots);
    }
  }

  public void acceptNestedData(final Set<NestedCopyInfo> set) {
    myNestedCopiesHolder.add(set);
  }

  private boolean init() {
    synchronized (myMonitor) {
      final boolean result = myInitialized;
      myInitialized = true;
      return result;
    }
  }

  public void realRefresh(final Runnable afterRefreshCallback) {
    if (myProject.isDisposed()) {
      afterRefreshCallback.run();
    }
    else {
      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final VirtualFile[] roots = myRootsHelper.execute();
      final SvnRootsDetector rootsDetector = new SvnRootsDetector(vcs, this, myNestedCopiesHolder);
      // do not send additional request for nested copies when in init state
      rootsDetector.detectCopyRoots(roots, init(), afterRefreshCallback);
    }
  }

  public void applyDetectionResult(@NotNull SvnRootsDetector.Result result) {
    new NewRootsApplier(result).apply();
  }

  private class NewRootsApplier {

    @NotNull private final SvnRootsDetector.Result myResult;
    @NotNull private final SvnMapping myNewMapping;
    @NotNull private final SvnMapping myNewFilteredMapping;

    private NewRootsApplier(@NotNull SvnRootsDetector.Result result) {
      myResult = result;
      myNewMapping = new SvnMapping();
      myNewFilteredMapping = new SvnMapping();
    }

    public void apply() {
      myNewMapping.addAll(myResult.getTopRoots());
      myNewMapping.reportLonelyRoots(myResult.getLonelyRoots());
      myNewFilteredMapping.addAll(new UniqueRootsFilter().filter(myResult.getTopRoots()));

      runUpdateMappings();
    }

    private void runUpdateMappings() {
      // TODO: Not clear so far why read action is used here - may be because of ROOTS_RELOADED message sent?
      ApplicationManager.getApplication().runReadAction(() -> {
        if (myProject.isDisposed()) return;

        boolean mappingsChanged = updateMappings();

        notifyRootsReloaded(mappingsChanged);
      });
    }

    private boolean updateMappings() {
      boolean mappingsChanged;
      synchronized (myMonitor) {
        mappingsChanged = ! myMoreRealMapping.equals(myNewFilteredMapping);
        mappingsChanged |= !myErrorRoots.equals(myResult.getErrorRoots());

        myMapping.copyFrom(myNewMapping);
        myMoreRealMapping.copyFrom(myNewFilteredMapping);
        myErrorRoots.clear();
        myErrorRoots.addAll(myResult.getErrorRoots());
      }
      return mappingsChanged;
    }

    private void notifyRootsReloaded(boolean mappingsChanged) {
      if (mappingsChanged || ! myInitedReloaded) {
        myInitedReloaded = true;
        // all listeners are asynchronous
        BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.ROOTS_RELOADED).consume(true);
        BackgroundTaskUtil.syncPublisher(myProject, ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN).directoryMappingChanged();
      } else {
        BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.ROOTS_RELOADED).consume(false);
      }
    }
  }

  @Nullable
  public String getUrlRootForUrl(final String currentUrl) {
    for (String url : myMoreRealMapping.getUrls()) {
      if (SVNPathUtil.isAncestor(url, currentUrl)) {
        return url;
      }
    }
    return null;
  }

  @Nullable
  public String getRootForPath(final File currentPath) {
    String convertedPath = currentPath.getAbsolutePath();
    convertedPath = (currentPath.isDirectory() && (! convertedPath.endsWith(File.separator))) ? convertedPath + File.separator :
        convertedPath;
    synchronized (myMonitor) {
      return myMoreRealMapping.getRootForPath(convertedPath);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getNotFilteredRoots() {
    return myRootsHelper.execute();
  }

  @Override
  public boolean isEmpty() {
    synchronized (myMonitor) {
      return myMapping.isEmpty();
    }
  }

  @Override
  public SvnMappingSavedPart getState() {
    final SvnMappingSavedPart result = new SvnMappingSavedPart();

    final SvnMapping mapping = new SvnMapping();
    final SvnMapping realMapping = new SvnMapping();
    synchronized (myMonitor) {
      mapping.copyFrom(myMapping);
      realMapping.copyFrom(myMoreRealMapping);
    }

    for (RootUrlInfo info : mapping.getAllCopies()) {
      result.add(convert(info));
    }
    for (RootUrlInfo info : realMapping.getAllCopies()) {
      result.addReal(convert(info));
    }
    return result;
  }

  private static SvnCopyRootSimple convert(final RootUrlInfo info) {
    final SvnCopyRootSimple copy = new SvnCopyRootSimple();
    copy.myVcsRoot = FileUtil.toSystemDependentName(info.getRoot().getPath());
    copy.myCopyRoot = info.getIoFile().getAbsolutePath();
    return copy;
  }

  @Override
  public void loadState(final SvnMappingSavedPart state) {
    ((ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject)).addInitializationRequest(
      VcsInitObject.AFTER_COMMON, (DumbAwareRunnable)() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final SvnMapping mapping = new SvnMapping();
        final SvnMapping realMapping = new SvnMapping();
        try {
          fillMapping(mapping, state.getMappingRoots());
          fillMapping(realMapping, state.getMoreRealMappingRoots());
        } catch (ProcessCanceledException e) {
          throw e;
        } catch (Throwable t) {
          LOG.info(t);
          return;
        }

        synchronized (myMonitor) {
          myMapping.copyFrom(mapping);
          myMoreRealMapping.copyFrom(realMapping);
        }
      }));
  }

  private void fillMapping(final SvnMapping mapping, final List<SvnCopyRootSimple> list) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();

    for (SvnCopyRootSimple simple : list) {
      final VirtualFile copyRoot = lfs.findFileByIoFile(new File(simple.myCopyRoot));
      final VirtualFile vcsRoot = lfs.findFileByIoFile(new File(simple.myVcsRoot));

      if (copyRoot == null || vcsRoot == null) continue;

      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final Info svnInfo = vcs.getInfo(copyRoot);
      if ((svnInfo == null) || (svnInfo.getRepositoryRootURL() == null)) continue;

      Node node = new Node(copyRoot, svnInfo.getURL(), svnInfo.getRepositoryRootURL());
      final RootUrlInfo info = new RootUrlInfo(node, SvnFormatSelector.findRootAndGetFormat(svnInfo.getFile()), vcsRoot);

      mapping.add(info);
    }
  }
}
