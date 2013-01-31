/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ThreadLocalDefendedInvoker;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@State(
  name = "SvnFileUrlMappingImpl",
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )}
)
public class SvnFileUrlMappingImpl implements SvnFileUrlMapping, PersistentStateComponent<SvnMappingSavedPart>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileUrlMappingImpl");

  private final SvnCompatibilityChecker myChecker;
  private final Object myMonitor = new Object();
  // strictly: what real roots are under what vcs mappings
  private final SvnMapping myMapping;
  // grouped; if there are several mappings one under another, will return the upmost
  private final SvnMapping myMoreRealMapping;
  private final MyRootsHelper myHelper;
  private final Project myProject;
  private final NestedCopiesSink myTempSink;
  private boolean myInitialized;
  private boolean myInitedReloaded;

  private static class MyRootsHelper extends ThreadLocalDefendedInvoker<VirtualFile[]> {
    private final ProjectLevelVcsManager myPlVcsManager;

    private MyRootsHelper(final ProjectLevelVcsManager vcsManager) {
      myPlVcsManager = vcsManager;
    }

    protected VirtualFile[] execute(Project project) {
      return myPlVcsManager.getRootsUnderVcs(SvnVcs.getInstance(project));
    }
  }

  public static SvnFileUrlMappingImpl getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, SvnFileUrlMappingImpl.class);
  }

  private SvnFileUrlMappingImpl(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myMapping = new SvnMapping();
    myMoreRealMapping = new SvnMapping();
    myHelper = new MyRootsHelper(vcsManager);
    myChecker = new SvnCompatibilityChecker(project);
    myTempSink = new NestedCopiesSink();
  }

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

  @Nullable
  public String getLocalPath(final String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url);
      if (rootUrl == null) {
        return null;
      }
      final RootUrlInfo parentInfo = myMoreRealMapping.byUrl(rootUrl);
      if (parentInfo == null) {
        return null;
      }

      return fileByUrl(parentInfo.getIoFile().getAbsolutePath(), rootUrl, url).getAbsolutePath();
    }
  }

  public static File fileByUrl(final String parentPath, final String parentUrl, final String childUrl) {
    return new File(parentPath, childUrl.substring(parentUrl.length()));
  }

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

  public boolean rootsDiffer() {
    synchronized (myMonitor) {
      return myMapping.isRootsDifferFromSettings();
    }
  }

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
  public List<RootUrlInfo> getAllWcInfos() {
    synchronized (myMonitor) {
      // a copy is created inside
      return myMoreRealMapping.getAllCopies();
    }
  }

  public List<VirtualFile> convertRoots(final List<VirtualFile> result) {
    if (ThreadLocalDefendedInvoker.isInside()) return result;

    synchronized (myMonitor) {
      final List<VirtualFile> cachedRoots = myMoreRealMapping.getUnderVcsRoots();
      final List<VirtualFile> lonelyRoots = myMoreRealMapping.getLonelyRoots();
      if (! lonelyRoots.isEmpty()) {
        myChecker.reportNoRoots(lonelyRoots);
      }
      if (cachedRoots.isEmpty()) {
        // todo +-
        return result;
      }
      return cachedRoots;
    }
  }

  public void acceptNestedData(final Set<NestedCopiesBuilder.MyPointInfo> set) {
    myTempSink.add(set);
  }

  private boolean init() {
    synchronized (myMonitor) {
      final boolean result = myInitialized;
      myInitialized = true;
      return result;
    }
  }

  public void realRefresh() {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final VirtualFile[] roots = myHelper.executeDefended(myProject);

    final CopiesApplier copiesApplier = new CopiesApplier();
    final CopiesDetector copiesDetector = new CopiesDetector(vcs, copiesApplier, new Getter<NestedCopiesData>() {
      public NestedCopiesData get() {
        return myTempSink.receive();
      }
    });
    // do not send additional request for nested copies when in init state
    copiesDetector.detectCopyRoots(roots, init());
  }

  private class CopiesApplier {
    public void apply(final SvnVcs vcs, final List<RootUrlInfo> roots, final List<VirtualFile> lonelyRoots) {
      final SvnMapping mapping = new SvnMapping();
      mapping.addAll(roots);
      mapping.reportLonelyRoots(lonelyRoots);

      final SvnMapping groupedMapping = new SvnMapping();
      final List<RootUrlInfo> filtered = new ArrayList<RootUrlInfo>();
      ForNestedRootChecker.filterOutSuperfluousChildren(vcs, roots, filtered);

      groupedMapping.addAll(filtered);

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (myProject.isDisposed()) return;
          boolean mappingsChanged = false;
          synchronized (myMonitor) {
            mappingsChanged = ! myMapping.equals(mapping);
            if (mappingsChanged) {
              mappingsChanged = ! myMoreRealMapping.equals(groupedMapping);
            }
            myMapping.copyFrom(mapping);
            myMoreRealMapping.copyFrom(groupedMapping);
          }

          if (mappingsChanged || ! myInitedReloaded) {
            myInitedReloaded = true;
            // all listeners are asynchronous
            final MessageBus bus = myProject.getMessageBus();
            bus.syncPublisher(SvnVcs.ROOTS_RELOADED).run();
            bus.syncPublisher(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN).directoryMappingChanged();
          }
        }
      });
    }
  }

  private static class CopiesDetector {
    private final SvnVcs myVcs;
    private final CopiesApplier myApplier;
    private final List<VirtualFile> myLonelyRoots;
    private final List<RootUrlInfo> myTopRoots;
    private final RepositoryRoots myRepositoryRoots;
    private final Getter<NestedCopiesData> myGate;

    private CopiesDetector(final SvnVcs vcs, final CopiesApplier applier, final Getter<NestedCopiesData> gate) {
      myVcs = vcs;
      myApplier = applier;
      myGate = gate;
      myTopRoots = new ArrayList<RootUrlInfo>();
      myLonelyRoots = new ArrayList<VirtualFile>();
      myRepositoryRoots = new RepositoryRoots(myVcs);
    }

    public void detectCopyRoots(final VirtualFile[] roots, final boolean clearState) {
      final Getter<Boolean> cancelGetter = new Getter<Boolean>() {
        public Boolean get() {
          return myVcs.getProject().isDisposed();
        }
      };

      for (final VirtualFile vcsRoot : roots) {
        final List<Real> foundRoots = ForNestedRootChecker.getAllNestedWorkingCopies(vcsRoot, myVcs, false, cancelGetter);
        if (foundRoots.isEmpty()) {
          myLonelyRoots.add(vcsRoot);
        }
        // filter out bad(?) items
        for (Real foundRoot : foundRoots) {
          final SVNURL repoRoot = foundRoot.getInfo().getRepositoryRootURL();
          if (repoRoot == null) {
            LOG.info("Error: cannot find repository URL for versioned folder: " + foundRoot.getFile().getPath());
          } else {
            myRepositoryRoots.register(repoRoot);
            myTopRoots.add(new RootUrlInfo(repoRoot, foundRoot.getInfo().getURL(),
                                       SvnFormatSelector.getWorkingCopyFormat(new File(foundRoot.getFile().getPath())), foundRoot.getFile(), vcsRoot));
          }
        }
      }

      addNestedRoots(clearState);
    }

    private void addNestedRoots(final boolean clearState) {
      final List<VirtualFile> basicVfRoots = ObjectsConvertor.convert(myTopRoots, new Convertor<RootUrlInfo, VirtualFile>() {
        public VirtualFile convert(final RootUrlInfo real) {
          return real.getVirtualFile();
        }
      });

      final ChangeListManager clManager = ChangeListManager.getInstance(myVcs.getProject());

      if (clearState) {
        // clear what was reported before (could be for currently-not-existing roots)
        myGate.get();
      }
      clManager.invokeAfterUpdate(new Runnable() {
        public void run() {
          final List<RootUrlInfo> nestedRoots = new ArrayList<RootUrlInfo>();

          final NestedCopiesData data = myGate.get();
          for (NestedCopiesBuilder.MyPointInfo info : data.getSet()) {
            if (NestedCopyType.external.equals(info.getType()) || NestedCopyType.switched.equals(info.getType())) {
              final File infoFile = new File(info.getFile().getPath());
              boolean copyFound = false;
              for (RootUrlInfo topRoot : myTopRoots) {
                if (topRoot.getIoFile().equals(infoFile)) {
                  topRoot.setType(info.getType());
                  copyFound = true;
                  break;
                }
              }
              if (copyFound) {
                continue;
              }
              try {
                final SVNStatus svnStatus = SvnUtil.getStatus(myVcs, infoFile);
                if (svnStatus.getURL() == null) continue;
                info.setUrl(svnStatus.getURL());
                info.setFormat(WorkingCopyFormat.getInstance(svnStatus.getWorkingCopyFormat()));
              }
              catch (Exception e) {
                continue;
              }
            }
            for (RootUrlInfo topRoot : myTopRoots) {
              if (VfsUtil.isAncestor(topRoot.getVirtualFile(), info.getFile(), true)) {
                SVNURL repoRoot = info.getRootURL();
                repoRoot = repoRoot == null ? myRepositoryRoots.ask(info.getUrl(), true) : repoRoot;
                if (repoRoot != null) {
                  final RootUrlInfo rootInfo = new RootUrlInfo(repoRoot, info.getUrl(), info.getFormat(), info.getFile(), topRoot.getRoot());
                  rootInfo.setType(info.getType());
                  nestedRoots.add(rootInfo);
                }
                break;
              }
            }
          }
          // check those top roots which ARE externals, but that was not detected due to they itself were the status request target
          //new SvnNestedTypeRechecker(myVcs.getProject(), myTopRoots).run();

          myTopRoots.addAll(nestedRoots);
          myApplier.apply(myVcs, myTopRoots, myLonelyRoots);
        }
      }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, null, new Consumer<VcsDirtyScopeManager>() {
        public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
          if (clearState) {
            vcsDirtyScopeManager.filesDirty(null, basicVfRoots);
          }
        }
      }, null);
    }
  }

  private static class RepositoryRoots {
    private final SvnVcs myVcs;
    private final Set<SVNURL> myRoots;

    private RepositoryRoots(final SvnVcs vcs) {
      myVcs = vcs;
      myRoots = new HashSet<SVNURL>();
    }

    public void register(final SVNURL url) {
      myRoots.add(url);
    }

    public SVNURL ask(final SVNURL url, boolean allowRemote) {
      for (SVNURL root : myRoots) {
        if (root.equals(SVNURLUtil.getCommonURLAncestor(root, url))) {
          return root;
        }
      }
      final SVNURL newUrl;
      try {
        newUrl = SvnUtil.getRepositoryRoot(myVcs, url, allowRemote);
        if (newUrl != null) {
          myRoots.add(newUrl);
          return newUrl;
        }
      }
      catch (SVNException e) {
        //
      }
      return null;
    }
  }

  private static class RepoInfo {
    private final SVNURL myUrl;
    private final boolean myRepoSupportsMergeinfo;

    private RepoInfo(final SVNURL url, final boolean repoSupportsMergeinfo) {
      myRepoSupportsMergeinfo = repoSupportsMergeinfo;
      myUrl = url;
    }

    public boolean isRepoSupportsMergeinfo() {
      return myRepoSupportsMergeinfo;
    }

    public SVNURL getUrl() {
      return myUrl;
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

  public VirtualFile[] getNotFilteredRoots() {
    return myHelper.executeDefended(myProject);
  }

  public boolean isEmpty() {
    synchronized (myMonitor) {
      return myMapping.isEmpty();
    }
  }

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

  private SvnCopyRootSimple convert(final RootUrlInfo info) {
    final SvnCopyRootSimple copy = new SvnCopyRootSimple();
    copy.myVcsRoot = FileUtil.toSystemDependentName(info.getRoot().getPath());
    copy.myCopyRoot = info.getIoFile().getAbsolutePath();
    return copy;
  }

  public void loadState(final SvnMappingSavedPart state) {
    ((ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject)).addInitializationRequest(
      VcsInitObject.AFTER_COMMON, new DumbAwareRunnable() {
        public void run() {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
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
            }
          });
        }
    });
  }

  private void fillMapping(final SvnMapping mapping, final List<SvnCopyRootSimple> list) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();

    for (SvnCopyRootSimple simple : list) {
      final VirtualFile copyRoot = lfs.findFileByIoFile(new File(simple.myCopyRoot));
      final VirtualFile vcsRoot = lfs.findFileByIoFile(new File(simple.myVcsRoot));

      if (copyRoot == null || vcsRoot == null) continue;

      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final SVNInfo svnInfo = vcs.getInfo(copyRoot);
      if ((svnInfo == null) || (svnInfo.getRepositoryRootURL() == null)) continue;

      final RootUrlInfo info = new RootUrlInfo(svnInfo.getRepositoryRootURL(), svnInfo.getURL(),
                                               SvnFormatSelector.getWorkingCopyFormat(svnInfo.getFile()), copyRoot, vcsRoot);
      mapping.add(info);
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "SvnFileUrlMappingImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
