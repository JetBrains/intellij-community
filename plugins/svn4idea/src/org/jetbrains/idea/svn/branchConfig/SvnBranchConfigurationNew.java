/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;
import java.util.*;

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
    final List<String> cutList = ObjectsConvertor.convert(result, new Convertor<String, String>() {
      @Override
      public String convert(String s) {
        return cutEndSlash(s);
      }
    });
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
      if (SVNPathUtil.isAncestor(myTrunkUrl, url)) {
        return cutEndSlash(myTrunkUrl);
      }
    }
    for(String branchUrl: myBranchMap.keySet()) {
      if (SVNPathUtil.isAncestor(branchUrl, url)) {
        String relativePath = SVNPathUtil.getRelativePath(branchUrl, url);
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
  public SVNURL getWorkingBranch(@NotNull SVNURL someUrl) throws SvnBindException {
    String baseUrl = getBaseUrl(someUrl.toString());
    return baseUrl == null ? null : SvnUtil.createUrl(baseUrl);
  }

  private void iterateUrls(final UrlListener listener) throws SVNException {
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
    } catch (SVNException e) {
      return null;
    }
  }

  public void removeBranch(String url) {
    url = ensureEndSlash(url);
    myBranchMap.remove(url);
  }

  private static class BranchRootSearcher implements UrlListener {
    private final VirtualFile myRoot;
    private final SVNURL myRootUrl;
    // url path to file path
    private final Map<String, String> myBranchesUnder;

    private BranchRootSearcher(final SvnVcs vcs, final VirtualFile root) throws SVNException {
      myRoot = root;
      myBranchesUnder = new HashMap<>();
      final Info info = vcs.getInfo(myRoot.getPath());
      myRootUrl = info != null ? info.getURL() : null;
    }

    public boolean accept(final String url) throws SVNException {
      if (myRootUrl != null) {
        final File baseDir = new File(myRoot.getPath());
        final String baseUrl = myRootUrl.getPath();

        final SVNURL branchUrl = SVNURL.parseURIEncoded(url);
        if (myRootUrl.equals(SVNURLUtil.getCommonURLAncestor(myRootUrl, branchUrl))) {
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
    boolean accept(final String url) throws SVNException;
  }
}
