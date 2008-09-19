package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
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

  private final Object myMonitor = new Object();
  private final Map<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, Pair<String, VirtualFile>> myUrl2FileMap;
  private final Map<String, VirtualFile> myFileRootsMap;
  private volatile boolean myUserRootsDiffersFromReal;

  SvnFileUrlMappingImpl(final Project project, final SvnVcs vcs) {
    myProject = project;
    myVcs = vcs;

    myFile2UrlMap = new HashMap<String, SvnFileUrlMappingRefresher.RootUrlInfo>();
    myUrl2FileMap = new HashMap<String, Pair<String, VirtualFile>>();
    myFileRootsMap = new HashMap<String, VirtualFile>();
  }

  @Nullable
  public SVNURL getUrlForFile(final File file) {
    final Pair<String, SvnFileUrlMappingRefresher.RootUrlInfo> rootInfo = getWcRootForFilePath(file);

    if (rootInfo == null) {
      return null;
    }
    final String absolutePath = file.getAbsolutePath();
    if (absolutePath.length() < rootInfo.first.length()) {
      // remove last separator from etalon name
      if (absolutePath.equals(rootInfo.first.substring(0, rootInfo.first.length() - 1))) {
        return rootInfo.second.getAbsoluteUrlAsUrl();
      }
      return null;
    }
    final String relativePath = absolutePath.substring(rootInfo.first.length());
    try {
      return rootInfo.second.getAbsoluteUrlAsUrl().appendPath(FileUtil.toSystemIndependentName(relativePath), true);
    }
    catch (SVNException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  public String getLocalPath(final String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
      if (rootUrl == null) {
        return null;
      }
      final String parentPath = myUrl2FileMap.get(rootUrl).first;

      return fileByUrl(parentPath, rootUrl, url).getAbsolutePath();
    }
  }

  @NotNull
  public List<VirtualFile> getWcRootsUnderVcsRoot(final VirtualFile vcsRoot) {
    synchronized (myMonitor) {
      final List<VirtualFile> result = new ArrayList<VirtualFile>();
      for (Map.Entry<String, VirtualFile> entry : myFileRootsMap.entrySet()) {
        if (Comparing.equal(vcsRoot, entry.getValue())) {
          final Pair<String, VirtualFile> fileInfo = myUrl2FileMap.get(entry.getKey());
          if (fileInfo != null) {
            final VirtualFile filePath = fileInfo.second;
            if (filePath != null) {
              result.add(filePath);
            }
          }
        }
      }
      return result;
    }
  }

  public VirtualFile getVcRootByUrl(final String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
      return myFileRootsMap.get(rootUrl);
    }
  }

  public static File fileByUrl(final String parentPath, final String parentUrl, final String childUrl) {
    return new File(parentPath, childUrl.substring(parentUrl.length()));
  }

  @Nullable
  public Pair<String, RootUrlInfo> getWcRootForFilePath(final File file) {
    synchronized (myMonitor) {
      final String root = getRootForPath(file);
      if (root == null) {
        return null;
      }

      return new Pair<String, RootUrlInfo>(root, myFile2UrlMap.get(root));
    }
  }

  public boolean rootsDiffer() {
    return myUserRootsDiffersFromReal;
  }

  @Nullable
  public RootMixedInfo getWcRootForUrl(final String url) {
    synchronized (myMonitor) {
      final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
      if (rootUrl == null) {
        return null;
      }

      final Pair<String, VirtualFile> filePair = myUrl2FileMap.get(rootUrl);
      if (filePair == null) {
        LOG.info("Inconsistent maps for url:" + url + " found root url: " + rootUrl);
        return null;
      }
      final SVNURL rootUrlUrl = myFile2UrlMap.get(filePair.first).getAbsoluteUrlAsUrl();
      return new RootMixedInfo(filePair.first, filePair.second, rootUrlUrl, myFileRootsMap.get(rootUrl));
    }
  }

  public Map<String, RootUrlInfo> getAllWcInfos() {
    synchronized (myMonitor) {
      final List<String> keys = new ArrayList<String>(myFile2UrlMap.keySet());
      Collections.sort(keys);

      final Map<String, RootUrlInfo> result = new HashMap<String, RootUrlInfo>();
      for (String key : keys) {
        boolean add = true;
        for (String path : result.keySet()) {
          if (key.startsWith(path)) {
            add = false;
            break;
          }
        }
        if (add) {
          result.put(key, myFile2UrlMap.get(key));
        }
      }
      return result;
    }
  }

  private class FileUrlMappingCrawler implements NotNullFunction<VirtualFile, Collection<VirtualFile>> {
    private final SVNWCClient myClient;
    private VirtualFile myCurrentRoot;
    private final Map<String, Pair<String, VirtualFile>> myUrl2FileMapCopy;
    private final Map<String, RootUrlInfo> myFile2UrlMapCopy;
    private final Map<String, VirtualFile> myFileRootsMapCopy;
    private boolean myRootsDiffer;

    private FileUrlMappingCrawler() {
      myUrl2FileMapCopy = new HashMap<String, Pair<String, VirtualFile>>();
      myFile2UrlMapCopy = new HashMap<String, RootUrlInfo>();
      myFileRootsMapCopy = new HashMap<String, VirtualFile>();
      myRootsDiffer = false;

      myClient = myVcs.createWCClient();
    }

    @NotNull
    public Collection<VirtualFile> fun(final VirtualFile virtualFile) {
      try {
        SVNInfo info = myVcs.getInfoWithCaching(virtualFile);
        if (info == null) {
          // === svn exception
          return Collections.emptyList();
        }

        final File ioFile = new File(virtualFile.getPath());
        SVNURL repositoryUrl = info.getRepositoryRootURL();
        if (repositoryUrl == null) {
          // in very few cases go there
          info = myClient.doInfo(ioFile, SVNRevision.HEAD);
          repositoryUrl = info.getRepositoryRootURL();
          if (repositoryUrl == null) {
            LOG.info("Error: cannot find repository URL for versioned folder: " + ioFile.getAbsolutePath());
            return Collections.emptyList();
          }
        }

        final String currentPath = FileUtil.toSystemDependentName(virtualFile.getPath()) + File.separator;

        final SvnFileUrlMappingRefresher.RootUrlInfo rootInfo = new SvnFileUrlMappingRefresher.RootUrlInfo(repositoryUrl, info.getURL(),
                                                                                           SvnFormatSelector.getWorkingCopyFormat(ioFile));

        if ((! myRootsDiffer) && (! myCurrentRoot.equals(virtualFile))) {
          myRootsDiffer = true;
        }
        myFile2UrlMapCopy.put(currentPath, rootInfo);
        myUrl2FileMapCopy.put(rootInfo.getAbsoluteUrl(), new Pair<String, VirtualFile>(currentPath, virtualFile));
        myFileRootsMapCopy.put(rootInfo.getAbsoluteUrl(), myCurrentRoot);
      }
      catch (SVNException e) {
        LOG.info(e);
      }

      return Collections.emptyList();
    }

    public void copyResults() {
      synchronized (myMonitor) {
        myUserRootsDiffersFromReal = myRootsDiffer;
        myFile2UrlMap.clear();
        myFile2UrlMap.putAll(myFile2UrlMapCopy);

        myFileRootsMap.clear();
        myFileRootsMap.putAll(myFileRootsMapCopy);

        myUrl2FileMap.clear();
        myUrl2FileMap.putAll(myUrl2FileMapCopy);
      }
    }

    public void setCurrentRoot(final VirtualFile currentRoot) {
      myCurrentRoot = currentRoot;
    }
  }

  public void doRefresh() {
    //LOG.info("do refresh: " + new Time(System.currentTimeMillis()));
    // will call convertsRoots
    ProjectLevelVcsManager.getInstanceChecked(myProject).getRootsUnderVcs(myVcs);
  }

  /**
   * for: convertions of roots in direct root search; update of roots in indirect root search
   */
  public List<VirtualFile> convertRoots(final List<VirtualFile> result) {
    final FileUrlMappingCrawler crawler = new FileUrlMappingCrawler();

    for (VirtualFile root : result) {
      crawler.setCurrentRoot(root);
      SvnUtil.crawlWCRoots(root, crawler);
    }

    final Collection<Pair<String, VirtualFile>> pairCollection;
    synchronized (myMonitor) {
      crawler.copyResults();
      pairCollection = myUrl2FileMap.values();
    }

    final List<VirtualFile> converted = new ArrayList<VirtualFile>();
    for (Pair<String, VirtualFile> pair : pairCollection) {
      converted.add(pair.getSecond());
    }
    return converted;
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
    final Set<String> paths;
    synchronized (myMonitor) {
      paths = myFile2UrlMap.keySet();
    }

    for (String path : paths) {
      if (FileUtil.startsWith(convertedPath, path)) {
        return path;
      }
    }
    return null;
  }
}
