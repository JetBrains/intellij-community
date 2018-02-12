// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class SvnBranchConfigurationNew {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew");
  private String myTrunkUrl;
  // need public for serialization
  public Map<String, InfoStorage<List<SvnBranchItem>>> myBranchMap;
  private boolean myUserinfoInUrl;

  public SvnBranchConfigurationNew() {
    myTrunkUrl = "";
    myBranchMap = new HashMap<>();
  }

  public boolean isUserinfoInUrl() {
    return myUserinfoInUrl;
  }

  public void setUserinfoInUrl(final boolean userinfoInUrl) {
    myUserinfoInUrl = userinfoInUrl;
  }

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  public List<String> getBranchUrls() {
    final ArrayList<String> result = new ArrayList<>(myBranchMap.keySet());
    List<String> cutList = map(result, SvnBranchConfigurationNew::cutEndSlash);
    Collections.sort(cutList);
    return cutList;
  }

  public void addBranches(String branchParentName, final InfoStorage<List<SvnBranchItem>> items) {
    branchParentName = ensureEndSlash(branchParentName);
    InfoStorage<List<SvnBranchItem>> current = myBranchMap.get(branchParentName);
    if (current != null) {
      LOG.info("Branches list not added for : '" + branchParentName + "; this branch parent URL is already present.");
      return;
    }
    myBranchMap.put(branchParentName, items);
  }

  public static String ensureEndSlash(String name) {
    return name.trim().endsWith("/") ? name : name + "/";
  }
  
  private static String cutEndSlash(String name) {
    return name.endsWith("/") && name.length() > 0 ? name.substring(0, name.length() - 1) : name;
  }

  public void updateBranch(String branchParentName, final InfoStorage<List<SvnBranchItem>> items) {
    branchParentName = ensureEndSlash(branchParentName);
    final InfoStorage<List<SvnBranchItem>> current = myBranchMap.get(branchParentName);
    if (current == null) {
      LOG.info("Branches list not updated for : '" + branchParentName + "; since config has changed.");
      return;
    }
    current.accept(items);
  }

  public Map<String, InfoStorage<List<SvnBranchItem>>> getBranchMap() {
    return myBranchMap;
  }

  public List<SvnBranchItem> getBranches(String url) {
    url = ensureEndSlash(url);
    return myBranchMap.get(url).getValue();
  }

  public SvnBranchConfigurationNew copy() {
    SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
    result.myUserinfoInUrl = myUserinfoInUrl;
    result.myTrunkUrl = myTrunkUrl;
    result.myBranchMap = new HashMap<>();
    for (Map.Entry<String, InfoStorage<List<SvnBranchItem>>> entry : myBranchMap.entrySet()) {
      final InfoStorage<List<SvnBranchItem>> infoStorage = entry.getValue();
      result.myBranchMap.put(entry.getKey(), new InfoStorage<>(
        new ArrayList<>(infoStorage.getValue()), infoStorage.getInfoReliability()));
    }
    return result;
  }

  @Nullable
  public String getBaseUrl(String url) {
    if (myTrunkUrl != null) {
      if (Url.isAncestor(myTrunkUrl, url)) {
        return cutEndSlash(myTrunkUrl);
      }
    }
    for(String branchUrl: myBranchMap.keySet()) {
      if (Url.isAncestor(branchUrl, url)) {
        String relativePath = Url.getRelative(branchUrl, url);
        int secondSlash = relativePath.indexOf("/");
        return cutEndSlash(branchUrl + (secondSlash == -1 ? relativePath : relativePath.substring(0, secondSlash)));
      }
    }
    return null;
  }

  @Nullable
  public String getBaseName(String url) {
    String baseUrl = getBaseUrl(url);
    if (baseUrl == null) {
      return null;
    }
    int lastSlash = baseUrl.lastIndexOf("/");
    return lastSlash == -1 ? baseUrl : baseUrl.substring(lastSlash + 1);
  }

  @Nullable
  public String getRelativeUrl(String url) {
    String baseUrl = getBaseUrl(url);
    return baseUrl == null ? null : url.substring(baseUrl.length());
  }

  @Nullable
  public Url getWorkingBranch(@NotNull Url someUrl) throws SvnBindException {
    String baseUrl = getBaseUrl(someUrl.toString());
    return baseUrl == null ? null : createUrl(baseUrl);
  }

  private void iterateUrls(final UrlListener listener) throws SvnBindException {
    if (listener.accept(myTrunkUrl)) {
      return;
    }

    for (String branchUrl : myBranchMap.keySet()) {
      // use more exact comparison first (paths longer)
      final List<SvnBranchItem> children = myBranchMap.get(branchUrl).getValue();
      for (SvnBranchItem child : children) {
        if (listener.accept(child.getUrl())) {
          return;
        }
      }

      /*if (listener.accept(branchUrl)) {
        return;
      }*/
    }
  }

  // to retrieve mappings between existing in the project working copies and their URLs
  @Nullable
  public Map<String,String> getUrl2FileMappings(final Project project, final VirtualFile root) {
    try {
      final BranchRootSearcher searcher = new BranchRootSearcher(SvnVcs.getInstance(project), root);
      iterateUrls(searcher);
      return searcher.getBranchesUnder();
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  public void removeBranch(String url) {
    url = ensureEndSlash(url);
    myBranchMap.remove(url);
  }

  private static class BranchRootSearcher implements UrlListener {
    private final VirtualFile myRoot;
    private final Url myRootUrl;
    // url path to file path
    private final Map<String, String> myBranchesUnder;

    private BranchRootSearcher(final SvnVcs vcs, final VirtualFile root) {
      myRoot = root;
      myBranchesUnder = new HashMap<>();
      final Info info = vcs.getInfo(myRoot.getPath());
      myRootUrl = info != null ? info.getURL() : null;
    }

    public boolean accept(final String url) throws SvnBindException {
      if (myRootUrl != null) {
        final File baseDir = virtualToIoFile(myRoot);
        final String baseUrl = myRootUrl.getPath();

        final Url branchUrl = createUrl(url);
        if (isAncestor(myRootUrl, branchUrl)) {
          final File file = SvnUtil.fileFromUrl(baseDir, baseUrl, branchUrl.getPath());
          myBranchesUnder.put(url, file.getAbsolutePath());
        }
      }
      return false; // iterate everything
    }

    public Map<String, String> getBranchesUnder() {
      return myBranchesUnder;
    }
  }

  private interface UrlListener {
    boolean accept(final String url) throws SvnBindException;
  }
}
