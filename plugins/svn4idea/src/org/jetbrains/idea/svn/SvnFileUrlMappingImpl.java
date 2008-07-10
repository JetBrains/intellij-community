package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
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

  private final Map<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, Pair<String, VirtualFile>> myUrl2FileMap;
  private final Map<String, VirtualFile> myFileRootsMap;
  private boolean myUserRootsDiffersFromReal;

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
    final String relativePath = file.getAbsolutePath().substring(rootInfo.first.length());
    try {
      return rootInfo.second.getAbsoluteUrlAsUrl().appendPath(relativePath, true);
    }
    catch (SVNException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  public String getLocalPath(final String url) {
    final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
    if (rootUrl == null) {
      return null;
    }
    final String parentPath = myUrl2FileMap.get(rootUrl).first;

    return fileByUrl(parentPath, rootUrl, url).getAbsolutePath();
  }

  @NotNull
  public List<VirtualFile> getWcRootsUnderVcsRoot(final VirtualFile vcsRoot) {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Map.Entry<String, VirtualFile> entry : myFileRootsMap.entrySet()) {
      if (Comparing.equal(vcsRoot, entry.getValue())) {
        final VirtualFile filePath = myUrl2FileMap.get(entry.getKey()).second;
        if (filePath != null) {
          result.add(filePath);
        }
      }
    }
    return result;
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

  public boolean rootsDiffer() {
    return myUserRootsDiffersFromReal;
  }

  @Nullable
  public RootMixedInfo getWcRootForUrl(final String url) {
    final String rootUrl = getUrlRootForUrl(url, myUrl2FileMap.keySet());
    if (rootUrl == null) {
      return null;
    }

    final Pair<String, VirtualFile> filePair = myUrl2FileMap.get(rootUrl);
    final SVNURL rootUrlUrl = myFile2UrlMap.get(filePair.first).getAbsoluteUrlAsUrl();
    return new RootMixedInfo(filePair.first, filePair.second, rootUrlUrl, myFileRootsMap.get(rootUrl));
  }

  public Map<String, RootUrlInfo> getAllWcInfos() {
    return Collections.unmodifiableMap(myFile2UrlMap);
  }

  private class FileUrlMappingCrawler implements NotNullFunction<VirtualFile, Collection<VirtualFile>> {
    private final SVNWCClient myClient;
    private VirtualFile myCurrentRoot;

    private FileUrlMappingCrawler() {
      myUrl2FileMap.clear();
      myFile2UrlMap.clear();

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

        SVNURL repositoryUrl = info.getRepositoryRootURL();
        if (repositoryUrl == null) {
          final File ioFile = new File(virtualFile.getPath());
          // in very few cases go there
          info = myClient.doInfo(ioFile, SVNRevision.HEAD);
          repositoryUrl = info.getRepositoryRootURL();
          if (repositoryUrl == null) {
            LOG.info("Error: cannot find repository URL for versioned folder: " + ioFile.getAbsolutePath());
            return Collections.emptyList();
          }
        }

        final String repositoryUrlString = repositoryUrl.toString();
        final String currentPath = FileUtil.toSystemDependentName(virtualFile.getPath()) + File.separator;

        final SvnFileUrlMappingRefresher.RootUrlInfo rootInfo = new SvnFileUrlMappingRefresher.RootUrlInfo(repositoryUrlString, info.getURL());

        if ((! myUserRootsDiffersFromReal) && (! myCurrentRoot.equals(virtualFile))) {
          myUserRootsDiffersFromReal = true;
        }
        myFile2UrlMap.put(currentPath, rootInfo);
        myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), new Pair<String, VirtualFile>(currentPath, virtualFile));
        myFileRootsMap.put(rootInfo.getAbsoluteUrl(), myCurrentRoot);
      }
      catch (SVNException e) {
        LOG.info(e);
      }

      return Collections.emptyList();
    }

    public void setCurrentRoot(final VirtualFile currentRoot) {
      myCurrentRoot = currentRoot;
    }
  }

  public void doRefresh() {
    //LOG.info("do refresh: " + new Time(System.currentTimeMillis()));
    myUserRootsDiffersFromReal = false;
    // look WC roots under mappings
    final List<VcsDirectoryMapping> mappings = ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings(myVcs);
    // WC roots
    final List<VirtualFile> wcRoots = new ArrayList<VirtualFile>();
    for (VcsDirectoryMapping mapping : mappings) {
      final VirtualFile directory = SvnUtil.correctRoot(myProject, mapping.getDirectory());
      if (directory == null) {
        continue;
      }
      wcRoots.add(LocalFileSystem.getInstance().findFileByPath(directory.getPath()));
    }

    final FileUrlMappingCrawler crawler = new FileUrlMappingCrawler();

    for (VirtualFile root : wcRoots) {
      crawler.setCurrentRoot(root);
      SvnUtil.crawlWCRoots(root, crawler);
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
