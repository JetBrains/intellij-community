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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

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
    myBranchMap = new HashMap<String, InfoStorage<List<SvnBranchItem>>>();
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
    final ArrayList<String> result = new ArrayList<String>(myBranchMap.keySet());
    Collections.sort(result);
    return result;
  }

  public void addBranches(final String branchParentName, final InfoStorage<List<SvnBranchItem>> items) {
    InfoStorage<List<SvnBranchItem>> current = myBranchMap.get(branchParentName);
    if (current != null) {
      LOG.info("Branches list not added for : '" + branchParentName + "; this branch parent URL is already present.");
      return;
    }
    myBranchMap.put(branchParentName, items);
  }

  public void updateBranch(final String branchParentName, final InfoStorage<List<SvnBranchItem>> items) {
    final InfoStorage<List<SvnBranchItem>> current = myBranchMap.get(branchParentName);
    if (current == null) {
      LOG.info("Branches list not updated for : '" + branchParentName + "; since config has changed.");
      return;
    }
    current.accept(items, null);
  }

  public Map<String, InfoStorage<List<SvnBranchItem>>> getBranchMap() {
    return myBranchMap;
  }

  public List<SvnBranchItem> getBranches(String url) {
    return myBranchMap.get(url).getValue();
  }

  /*public SvnBranchConfiguration createFromVcsRootBranches() {
    final SvnBranchConfiguration result = new SvnBranchConfiguration();
    result.setTrunkUrl(myTrunkUrl);
    final Map<String, List<SvnBranchItem>> branchMap = result.getBranchMap();
    for (String key : myBranchMap.keySet()) {
      result.setTrunkUrl(key);
      final List<SvnBranchItem> list = myBranchMap.get(key).getT();
      branchMap.put(key, list);
    }
    return result;
  }*/

  public SvnBranchConfigurationNew copy() {
    SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
    result.myUserinfoInUrl = myUserinfoInUrl;
    result.myTrunkUrl = myTrunkUrl;
    result.myBranchMap = new HashMap<String, InfoStorage<List<SvnBranchItem>>>();
    for (Map.Entry<String, InfoStorage<List<SvnBranchItem>>> entry : myBranchMap.entrySet()) {
      final InfoStorage<List<SvnBranchItem>> infoStorage = entry.getValue();
      result.myBranchMap.put(entry.getKey(), new InfoStorage<List<SvnBranchItem>>(
        new ArrayList<SvnBranchItem>(infoStorage.getValue()), infoStorage.getInfoReliability()));
    }
    return result;
  }

  @Nullable
  public String getBaseUrl(String url) {
    if ((myTrunkUrl != null) && url.startsWith(myTrunkUrl)) {
      return myTrunkUrl;
    }
    for(String branchUrl: myBranchMap.keySet()) {
      if (url.startsWith(branchUrl)) {
        int pos = url.indexOf('/', branchUrl.length()+1);
        if (pos >= 0) {
          return url.substring(0, pos);
        }
        return branchUrl;
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
    if (myBranchMap.isEmpty()) {
      return baseUrl;
    }
    int commonPrefixLength = getCommonPrefixLength(url, myTrunkUrl);
    for (String branchUrl : myBranchMap.keySet()) {
      commonPrefixLength = Math.min(commonPrefixLength, getCommonPrefixLength(url, branchUrl));
      if (commonPrefixLength <= 0) {
        return baseUrl;
      }
    }
    return baseUrl.substring(commonPrefixLength);
  }

  private static int getCommonPrefixLength(final String s1, final String s2) {
    final int minLength = Math.min(s1.length(), s2.length());
    for(int i=0; i< minLength; i++) {
      if (s1.charAt(i) != s2.charAt(i)) {
        return i;
      }
    }
    return minLength;
  }

  @Nullable
  public String getRelativeUrl(String url) {
    String baseUrl = getBaseUrl(url);
    return baseUrl == null ? null : url.substring(baseUrl.length());
  }

  @Nullable
  public SVNURL getWorkingBranch(final SVNURL someUrl) throws SVNException {
    final BranchSearcher branchSearcher = new BranchSearcher(someUrl);
    iterateUrls(branchSearcher);

    return branchSearcher.getResult();
  }

  // todo +-
  @Nullable
  public String getGroupToLoadToReachUrl(final SVNURL url) throws SVNException {
    final BranchSearcher branchSearcher = new BranchSearcher(url);
    for (String group : myBranchMap.keySet()) {
      if (branchSearcher.accept(group)) {
        return group;
      }
    }
    return null;
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

  public void removeBranch(final String url) {
    myBranchMap.remove(url);
  }

  private static class BranchRootSearcher implements UrlListener {
    private final VirtualFile myRoot;
    private final SVNURL myRootUrl;
    // url path to file path
    private final Map<String, String> myBranchesUnder;

    private BranchRootSearcher(final SvnVcs vcs, final VirtualFile root) throws SVNException {
      myRoot = root;
      myBranchesUnder = new HashMap<String, String>();
      final SVNWCClient client = vcs.createWCClient();
      final SVNInfo info = client.doInfo(new File(myRoot.getPath()), SVNRevision.WORKING);
      myRootUrl = info.getURL();
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

  private static class BranchSearcher implements UrlListener {
    private final SVNURL mySomeUrl;
    private SVNURL myResult;

    private BranchSearcher(final SVNURL someUrl) {
      mySomeUrl = someUrl;
    }

    public boolean accept(final String url) throws SVNException {
      myResult = urlIsParent(url, mySomeUrl);
      return myResult != null;
    }

    public SVNURL getResult() {
      return myResult;
    }
  }

  @Nullable
  private static SVNURL urlIsParent(final String parentCandidate, final SVNURL child) throws SVNException {
    final SVNURL parentUrl = SVNURL.parseURIEncoded(parentCandidate);
    if(parentUrl.equals(SVNURLUtil.getCommonURLAncestor(parentUrl, child))) {
      return parentUrl;
    }
    return null;
  }
}
