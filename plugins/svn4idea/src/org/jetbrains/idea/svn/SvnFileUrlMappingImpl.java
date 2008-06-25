package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

class SvnFileUrlMappingImpl implements SvnFileUrlMappingRefresher.RefreshableSvnFileUrlMapping {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileUrlMappingImpl");
  
  private final Project myProject;
  private final SvnVcs myVcs;

  private final Map<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, String> myUrl2FileMap;
  private final Map<String, VirtualFile> myFileRootsMap;

  SvnFileUrlMappingImpl(final Project project, final SvnVcs vcs) {
    myProject = project;
    myVcs = vcs;

    myFile2UrlMap = new HashMap<String, SvnFileUrlMappingRefresher.RootUrlInfo>();
    myUrl2FileMap = new HashMap<String, String>();
    myFileRootsMap = new HashMap<String, VirtualFile>();
  }

  @Nullable
  public SVNURL getUrlForFile(final File file) {
    final Pair<String, SvnFileUrlMappingRefresher.RootUrlInfo> rootInfo = getWcRootForFilePath(file);

    if (rootInfo == null) {
      return null;
    }
    final String relativePath = file.getAbsolutePath().substring(rootInfo.first.length());
    try {
      return rootInfo.second.getAbsoluteUrlAsUrl().appendPath(relativePath, true);
    }
    catch (SVNException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  public String getLocalPath(final String url) {
    final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
    if (rootUrl == null) {
      return null;
    }
    final String parentPath = myUrl2FileMap.get(rootUrl);

    return fileByUrl(parentPath, rootUrl, url).getAbsolutePath();
  }

  public VirtualFile getVcRootByUrl(final String url) {
    final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
    return myFileRootsMap.get(rootUrl);
  }

  public static File fileByUrl(final String parentPath, final String parentUrl, final String childUrl) {
    return new File(parentPath, childUrl.substring(parentUrl.length()));
  }

  @Nullable
  public Pair<String, RootUrlInfo> getWcRootForFilePath(final File file) {
    final String root = getRootForPath(file);
    if (root == null) {
      return null;
    }

    return new Pair<String, RootUrlInfo>(root, myFile2UrlMap.get(root));
  }

  @Nullable
  public RootMixedInfo getWcRootForUrl(final String url) {
    final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
    if (rootUrl == null) {
      return null;
    }

    final String file = myUrl2FileMap.get(rootUrl);
    final SVNURL rootUrlUrl = myFile2UrlMap.get(file).getAbsoluteUrlAsUrl();
    return new RootMixedInfo(file, rootUrlUrl, myFileRootsMap.get(rootUrl));
  }

  public Map<String, RootUrlInfo> getAllWcInfos() {
    return Collections.unmodifiableMap(myFile2UrlMap);
  }

  private class FileUrlMappingCrawler implements SvnWCRootCrawler {
    private final SVNWCClient myClient;
    private VirtualFile myCurrentRoot;

    private FileUrlMappingCrawler() {
      myUrl2FileMap.clear();
      myFile2UrlMap.clear();

      myClient = myVcs.createWCClient();
    }

    public Collection<File> handleWorkingCopyRoot(final File root, final ProgressIndicator progress) {
      // topmost versioned directory under directory set by the user
      // !!! not nessecarily WC root (root might be above)
      try {
        final SVNInfo info = myClient.doInfo(root, SVNRevision.WORKING);
        String currentPath = FileUtil.toSystemDependentName(root.getAbsolutePath()) + File.separator;
        final String repositoryUrl = info.getRepositoryRootURL().toString();

        final SvnFileUrlMappingRefresher.RootUrlInfo rootInfo = new SvnFileUrlMappingRefresher.RootUrlInfo(repositoryUrl, info.getURL());

        myFile2UrlMap.put(currentPath, rootInfo);
        myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), currentPath);
        myFileRootsMap.put(rootInfo.getAbsoluteUrl(), myCurrentRoot);
      }
      catch (SVNException e) {
        LOG.error(e);
      }
      
      return Collections.emptyList();
    }

    public void setCurrentRoot(final VirtualFile currentRoot) {
      myCurrentRoot = currentRoot;
    }
  }

  public void doRefresh() {
    //LOG.info("do refresh: " + new Time(System.currentTimeMillis()));
    // look WC roots under them.
    VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs);

    final FileUrlMappingCrawler crawler = new FileUrlMappingCrawler();

    for (VirtualFile root : roots) {
      final File ioFile = new File(root.getPath());
      crawler.setCurrentRoot(root);
      SvnUtil.crawlWCRoots(ioFile, crawler, ProgressManager.getInstance().getProgressIndicator());
    }
  }

  @Nullable
  public static String getUrlRootForUrl(final String currentUrl, final Set<String> set) {
    for (String url : set) {
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
    for (String path : myFile2UrlMap.keySet()) {
      if (FileUtil.startsWith(convertedPath, path)) {
        return path;
      }
    }
    return null;
  }
}
