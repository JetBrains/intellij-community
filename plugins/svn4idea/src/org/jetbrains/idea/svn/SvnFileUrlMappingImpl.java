package org.jetbrains.idea.svn;

import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.StringLenComparator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(
  name = "SvnFileUrlMappingImpl",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
class SvnFileUrlMappingImpl implements SvnFileUrlMapping, PersistentStateComponent<SvnMappingSavedPart>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileUrlMappingImpl");

  private final SvnCompatibilityChecker myChecker;

  private final Object myMonitor = new Object();

  // strictly: what real roots are under what vcs mappings
  private final SvnMapping myMapping;
  // grouped; if there are several mappings one under another, will return the upmost
  private final SvnMapping myMoreRealMapping;

  private final MyRootsHelper myHelper;

  private final Project myProject;

  private class MyRootsHelper extends ThreadLocalDefendedInvoker<VirtualFile[]> {
    private final ProjectLevelVcsManager myPlVcsManager;

    private MyRootsHelper(final ProjectLevelVcsManager vcsManager) {
      myPlVcsManager = vcsManager;
    }

    protected VirtualFile[] execute() {
      return myPlVcsManager.getRootsUnderVcs(SvnVcs.getInstance(myProject));
    }
  }

  public static SvnFileUrlMappingImpl getInstance(final Project project) {
    return project.getComponent(SvnFileUrlMappingImpl.class);
  }

  private SvnFileUrlMappingImpl(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myMapping = new SvnMapping();
    myMoreRealMapping = new SvnMapping();
    myHelper = new MyRootsHelper(vcsManager);
    myChecker = new SvnCompatibilityChecker(project);
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
      final RootUrlInfo parentInfo = myMapping.byUrl(rootUrl);
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

      return myMapping.byFile(root);
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

      final RootUrlInfo result = myMapping.byUrl(rootUrl);
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
    if (myHelper.isInside()) return result;

    synchronized (myMonitor) {
      final List<VirtualFile> cachedRoots = myMapping.getUnderVcsRoots();
      final List<VirtualFile> lonelyRoots = myMapping.getLonelyRoots();
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

  public void realRefresh(final AtomicSectionsAware atomicSectionsAware) {
    final boolean goIntoNested = SvnConfiguration.getInstance(myProject).DETECT_NESTED_COPIES;
    final SvnVcs vcs = SvnVcs.getInstance(myProject);

    final VirtualFile[] roots = myHelper.executeDefended();

    final SvnMapping mapping = new SvnMapping();

    final List<Real> allRoots = new ArrayList<Real>();
    for (final VirtualFile root : roots) {
      final List<Real> foundRoots = ForNestedRootChecker.getAllNestedWorkingCopies(root, vcs, goIntoNested, new Getter<Boolean>() {
        public Boolean get() {
          return atomicSectionsAware.shouldExitAsap();
        }
      });
      if (foundRoots.isEmpty()) {
        mapping.reportLonelyRoot(root);
      }
      allRoots.addAll(foundRoots);
      
      for (Real foundRoot : foundRoots) {
        addRoot(mapping, foundRoot);
      }
    }

    final SvnMapping groupedMapping = new SvnMapping();
    final List<Real> filtered = new ArrayList<Real>();
    ForNestedRootChecker.filterOutSuperfluousChildren(vcs, allRoots, filtered);

    for (Real copy : filtered) {
      addRoot(groupedMapping, copy);
    }

    try {
      atomicSectionsAware.enter();
      synchronized (myMonitor) {
        myMapping.copyFrom(mapping);
        myMoreRealMapping.copyFrom(groupedMapping);
      }
    } finally {
      atomicSectionsAware.exit();
    }
  }

  private void addRoot(final SvnMapping mapping, final Real foundRoot) {
    final SVNInfo info = foundRoot.getInfo();
    addRootImpl(mapping, foundRoot.getFile(), foundRoot.getVcsRoot(), info);
  }

  private void addRootImpl(final SvnMapping mapping, final VirtualFile copyRoot, final VirtualFile vcsRoot, final SVNInfo info) {
    if (info != null) {
      final SVNURL repositoryUrl = info.getRepositoryRootURL();
      if (repositoryUrl == null) {
        LOG.info("Error: cannot find repository URL for versioned folder: " + copyRoot.getPath());
        return;
      }

      mapping.add(copyRoot, vcsRoot, info, repositoryUrl);
    }
  }

  @Nullable
  public String getUrlRootForUrl(final String currentUrl) {
    for (String url : myMapping.getUrls()) {
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
    final List<String> paths;
    synchronized (myMonitor) {
      paths = new ArrayList<String>(myMapping.getFileRoots());
    }
    Collections.sort(paths, StringLenComparator.getDescendingInstance());

    for (String path : paths) {
      if (FileUtil.startsWith(convertedPath, path)) {
        return path;
      }
    }
    return null;
  }

  public VirtualFile[] getNotFilteredRoots() {
    return myHelper.executeDefended();
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
    final SvnMapping mapping = new SvnMapping();
    final SvnMapping realMapping = new SvnMapping();

    try {
      fillMapping(mapping, state.getMappingRoots());
      fillMapping(realMapping, state.getMoreRealMappingRoots());
    } catch (Throwable t) {
      LOG.info(t);
      return;
    }

    synchronized (myMonitor) {
      myMapping.copyFrom(mapping);
      myMoreRealMapping.copyFrom(realMapping);
    }
  }

  private void fillMapping(final SvnMapping mapping, final List<SvnCopyRootSimple> list) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    
    for (SvnCopyRootSimple simple : list) {
      final VirtualFile copyRoot = lfs.findFileByIoFile(new File(simple.myCopyRoot));
      final VirtualFile vcsRoot = lfs.findFileByIoFile(new File(simple.myVcsRoot));

      if (copyRoot == null || vcsRoot == null) continue;

      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final SVNInfo svnInfo = vcs.getInfo(copyRoot);
      if (svnInfo == null) continue;

      addRootImpl(mapping, copyRoot, vcsRoot, svnInfo);
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
