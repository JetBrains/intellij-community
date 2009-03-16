package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.StringLenComparator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SvnFileUrlMappingImpl implements SvnFileUrlMapping {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileUrlMappingImpl");

  private final SvnVcs myVcs;

  private final Object myMonitor = new Object();

  // strictly: what real roots are under what vcs mappings
  private final SvnMapping myMapping;
  // grouped; if there are several mappings one under another, will return the upmost
  private final SvnMapping myMoreRealMapping;

  private final MyRootsHelper myHelper;

  private static class MyRootsHelper {
    private final ProjectLevelVcsManager myPlVcsManager;
    private final SvnVcs myVcs;

    private final static ThreadLocal<Boolean> ourInsideRefresh = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.TRUE;
      }
    };

    private MyRootsHelper(final Project project, final SvnVcs vcs) {
      myVcs = vcs;
      myPlVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    public boolean doConvertion() {
      return Boolean.TRUE.equals(ourInsideRefresh.get());
    }

    public VirtualFile[] getRootSettings() {
      try {
        ourInsideRefresh.set(Boolean.FALSE);
        return myPlVcsManager.getRootsUnderVcs(myVcs);
      } finally {
        ourInsideRefresh.set(Boolean.TRUE);
      }
    }
  }

  SvnFileUrlMappingImpl(final Project project, final SvnVcs vcs) {
    myVcs = vcs;
    myMapping = new SvnMapping();
    myMoreRealMapping = new SvnMapping();
    myHelper = new MyRootsHelper(project, myVcs);
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
    if (! myHelper.doConvertion()) return result;

    synchronized (myMonitor) {
      final List<VirtualFile> cachedRoots = myMapping.getUnderVcsRoots();
      if (cachedRoots.isEmpty()) {
        // todo +-
        return result;
      }
      return cachedRoots;
    }
  }

  public void realRefresh() {
    final boolean goIntoNested = SvnConfiguration.getInstance(myVcs.getProject()).DETECT_NESTED_COPIES;

    final VirtualFile[] roots = myHelper.getRootSettings();

    final SvnMapping mapping = new SvnMapping();

    final List<Real> allRoots = new ArrayList<Real>();
    for (VirtualFile root : roots) {
      final List<Real> foundRoots = ForNestedRootChecker.getAllNestedWorkingCopies(root, myVcs, goIntoNested);
      allRoots.addAll(foundRoots);
      
      for (Real foundRoot : foundRoots) {
        addRoot(mapping, foundRoot);
      }
    }

    final SvnMapping groupedMapping = new SvnMapping();
    final List<Real> filtered = new ArrayList<Real>();
    ForNestedRootChecker.filterOutSuperfluousChildren(myVcs, allRoots, filtered);

    for (Real copy : filtered) {
      addRoot(groupedMapping, copy);
    }

    synchronized (myMonitor) {
      myMapping.copyFrom(mapping);
      myMoreRealMapping.copyFrom(groupedMapping);
    }
  }

  private void addRoot(final SvnMapping mapping, final Real foundRoot) {
    final SVNInfo info = foundRoot.getInfo();
    if (info != null) {
      final SVNURL repositoryUrl = info.getRepositoryRootURL();
      if (repositoryUrl == null) {
        LOG.info("Error: cannot find repository URL for versioned folder: " + foundRoot.getFile().getPath());
        return;
      }

      mapping.add(foundRoot.getFile(), foundRoot.getVcsRoot(), info, repositoryUrl);
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
    return myHelper.getRootSettings();
  }
}
