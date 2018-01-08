// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static org.jetbrains.idea.svn.SvnFormatSelector.findRootAndGetFormat;
import static org.jetbrains.idea.svn.SvnUtil.*;

@State(name = "SvnFileUrlMappingImpl", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SvnFileUrlMappingImpl implements SvnFileUrlMapping, PersistentStateComponent<SvnMappingSavedPart> {
  private static final Logger LOG = Logger.getInstance(SvnFileUrlMappingImpl.class);

  @NotNull private final SvnCompatibilityChecker myChecker;
  @NotNull private final Object myMonitor = new Object();
  // strictly: what real roots are under what vcs mappings
  @NotNull private final SvnMapping myMapping = new SvnMapping();
  // grouped; if there are several mappings one under another, will return the upmost
  @NotNull private final SvnMapping myMoreRealMapping = new SvnMapping();
  @NotNull private final List<RootUrlInfo> myErrorRoots = newArrayList();
  @NotNull private final MyRootsHelper myRootsHelper;
  @NotNull private final Project myProject;
  @NotNull private final NestedCopiesHolder myNestedCopiesHolder = new NestedCopiesHolder();
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
    myRootsHelper = new MyRootsHelper(project, vcsManager);
    myChecker = new SvnCompatibilityChecker(project);
  }

  @Override
  @Nullable
  public Url getUrlForFile(@NotNull File file) {
    Url result = null;
    RootUrlInfo rootUrlInfo = getWcRootForFilePath(file);

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
  public RootUrlInfo getWcRootForFilePath(@NotNull File file) {
    synchronized (myMonitor) {
      String convertedPath = file.getAbsolutePath();
      convertedPath = file.isDirectory() && !convertedPath.endsWith(File.separator) ? convertedPath + File.separator : convertedPath;
      String root = myMoreRealMapping.getRootForPath(convertedPath);

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
      return newArrayList(myErrorRoots);
    }
  }

  @Override
  @NotNull
  public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
    if (MyRootsHelper.isInProgress()) return newArrayList(result);

    synchronized (myMonitor) {
      List<VirtualFile> cachedRoots = myMoreRealMapping.getUnderVcsRoots();
      List<VirtualFile> lonelyRoots = myMoreRealMapping.getLonelyRoots();
      if (!lonelyRoots.isEmpty()) {
        myChecker.reportNoRoots(lonelyRoots);
      }

      return newArrayList(cachedRoots.isEmpty() ? result : cachedRoots);
    }
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

  public void realRefresh(final Runnable afterRefreshCallback) {
    if (myProject.isDisposed()) {
      afterRefreshCallback.run();
    }
    else {
      SvnVcs vcs = SvnVcs.getInstance(myProject);
      VirtualFile[] roots = myRootsHelper.execute();
      SvnRootsDetector rootsDetector = new SvnRootsDetector(vcs, this, myNestedCopiesHolder);
      // do not send additional request for nested copies when in init state
      rootsDetector.detectCopyRoots(roots, init(), afterRefreshCallback);
    }
  }

  public void applyDetectionResult(@NotNull SvnRootsDetector.Result result) {
    new NewRootsApplier(result).apply();
  }

  private class NewRootsApplier {

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
      } else {
        BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.ROOTS_RELOADED).consume(false);
      }
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
  public void loadState(final SvnMappingSavedPart state) {
    ((ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject)).addInitializationRequest(
      VcsInitObject.AFTER_COMMON, (DumbAwareRunnable)() -> getApplication().executeOnPooledThread(() -> {
        SvnMapping mapping = new SvnMapping();
        SvnMapping realMapping = new SvnMapping();
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

  private void fillMapping(@NotNull SvnMapping mapping, @NotNull List<SvnCopyRootSimple> list) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();

    for (SvnCopyRootSimple simple : list) {
      VirtualFile copyRoot = lfs.findFileByIoFile(new File(simple.myCopyRoot));
      VirtualFile vcsRoot = lfs.findFileByIoFile(new File(simple.myVcsRoot));

      if (copyRoot == null || vcsRoot == null) continue;

      SvnVcs vcs = SvnVcs.getInstance(myProject);
      Info info = vcs.getInfo(copyRoot);

      if (info != null && info.getRepositoryRootURL() != null) {
        Node node = new Node(copyRoot, info.getURL(), info.getRepositoryRootURL());
        mapping.add(new RootUrlInfo(node, findRootAndGetFormat(info.getFile()), vcsRoot));
      }
    }
  }
}
