/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class SvnBranchConfiguration {
  private String myTrunkUrl;
  private List<String> myBranchUrls;
  private Map<String, List<SvnBranchItem>> myBranchMap;

  public SvnBranchConfiguration() {
    myBranchUrls = new ArrayList<String>();
    myBranchMap = new HashMap<String, List<SvnBranchItem>>();
  }

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public void setBranchUrls(final List<String> branchUrls) {
    myBranchUrls = branchUrls;
    
    Collections.sort(myBranchUrls);
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  public List<String> getBranchUrls() {
    return myBranchUrls;
  }

  public Map<String, List<SvnBranchItem>> getBranchMap() {
    return myBranchMap;
  }

  public void setBranchMap(final Map<String, List<SvnBranchItem>> branchMap) {
    myBranchMap = branchMap;
  }

  public SvnBranchConfiguration copy() {
    SvnBranchConfiguration result = new SvnBranchConfiguration();
    result.myTrunkUrl = myTrunkUrl;
    result.myBranchUrls = new ArrayList<String>(myBranchUrls);
    result.myBranchMap = new HashMap<String, List<SvnBranchItem>>();
    for (Map.Entry<String, List<SvnBranchItem>> entry : myBranchMap.entrySet()) {
      result.myBranchMap.put(entry.getKey(), new ArrayList<SvnBranchItem>(entry.getValue()));
    }
    return result;
  }

  @Nullable
  public String getBaseUrl(String url) {
    if (url.startsWith(myTrunkUrl)) {
      return myTrunkUrl;
    }
    for(String branchUrl: myBranchUrls) {
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
    if (myBranchUrls.size() == 0) {
      return baseUrl;
    }
    int commonPrefixLength = getCommonPrefixLength(url, myTrunkUrl);
    for(String branchUrl: myBranchUrls) {
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
  public String getBranchByName(final Project project, final String name) throws SVNException {
    if (name == null) {
      return null;
    }
    final UrlIterator iterator = new UrlIterator(project);
    final MyBranchByNameSearcher listener = new MyBranchByNameSearcher(name);
    iterator.iterateUrls(listener);
    return listener.getUrl();
  }

  @Nullable
  public SVNURL getWorkingBranch(final Project project, final SVNURL someUrl) throws SVNException {
    final BranchSearcher branchSearcher = new BranchSearcher(someUrl);
    final UrlIterator iterator = new UrlIterator(project);
    iterator.iterateUrls(branchSearcher);

    return branchSearcher.getResult();
  }

  private class UrlIterator {
    private final Project myProject;

    private UrlIterator(final Project project) {
      myProject = project;
    }

    public void iterateUrls(final UrlListener listener) throws SVNException {
      if (listener.accept(myTrunkUrl)) {
        return;
      }

      for (String branchUrl : myBranchUrls) {
        // use more exact comparison first (paths longer)
        final List<SvnBranchItem> children = getBranches(branchUrl, myProject, false);
        for (SvnBranchItem child : children) {
          if (listener.accept(child.getUrl())) {
            return;
          }
        }

        if (listener.accept(branchUrl)) {
          return;
        }
      }
    }
  }

  @Nullable
  public Map<String,String> getUrl2FileMappings(final Project project, final VirtualFile root) {
    try {
      final BranchRootSearcher searcher = new BranchRootSearcher(SvnVcs.getInstance(project), root);
      final UrlIterator iterator = new UrlIterator(project);
      iterator.iterateUrls(searcher);
      return searcher.getBranchesUnder();
    } catch (SVNException e) {
      return null;
    }
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

  public void loadBranches(final Project project, final boolean underProgress) {
    for (String branchUrl : myBranchUrls) {
      try {
        getBranches(branchUrl, project, underProgress);
      }
      catch (SVNException e) {
        // clear current (may be incomplete; better to detect it and reload on demand)
        myBranchMap.remove(branchUrl);
      }
    }
  }

  public List<SvnBranchItem> reloadBranches(final String url, final Project project) throws SVNException {
    final List<SvnBranchItem> result = getBranchesUnderProgress(url, project);
    myBranchMap.put(url, result);

    return result;
  }

  public List<SvnBranchItem> getBranches(final String url, final Project project, final boolean underProgress) throws SVNException {
    List<SvnBranchItem> result = myBranchMap.get(url);
    if ((result != null) && (result.isEmpty() || (result.get(0).getCreationDateMillis() > 0))) {
      return result;
    }
    result = underProgress ? getBranchesUnderProgress(url, project) : getBranchesWithoutProgress(url, project);
    myBranchMap.put(url, result);

    return result;
  }

  private static List<SvnBranchItem> getBranchesUnderProgress(final String url, final Project project) throws SVNException {
    final ArrayList<SvnBranchItem> result = new ArrayList<SvnBranchItem>();
    final Ref<SVNException> ex = new Ref<SVNException>();
    boolean rc = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setIndeterminate(true);
        }
        try {
          getBranchesImpl(project, url, result, true);
        }
        catch (SVNException e) {
          ex.set(e);
        }
      }
    }, SvnBundle.message("compare.with.branch.progress.loading.branches"), true, project);
    if (!rc) {
      return Collections.emptyList();
    }
    if (!ex.isNull()) {
      throw ex.get();
    }
    return result;
  }

  private static List<SvnBranchItem> getBranchesWithoutProgress(final String url, final Project project) throws SVNException {
    final ArrayList<SvnBranchItem> result = new ArrayList<SvnBranchItem>();
    getBranchesImpl(project, url, result, false);
    return result;
  }

  private static void getBranchesImpl(final Project project, final String url, final ArrayList<SvnBranchItem> result,
                               final boolean underProgress) throws SVNException {
    final SVNLogClient logClient;
      logClient = SvnVcs.getInstance(project).createLogClient();
      logClient.doList(SVNURL.parseURIEncoded(url), SVNRevision.UNDEFINED, SVNRevision.HEAD, false, new ISVNDirEntryHandler() {
        public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
          if (underProgress) {
            ProgressManager.getInstance().checkCanceled();
          }
          final String url = dirEntry.getURL().toString();
          result.add(new SvnBranchItem(url, dirEntry.getDate(), dirEntry.getRevision()));
        }
      });
      Collections.sort(result);
  }

  public boolean urlsMissing(final SvnBranchConfiguration branch) {
    if (! Comparing.equal(myTrunkUrl, branch.getTrunkUrl())) {
      return true;
    }

    for (String url : branch.getBranchUrls()) {
      if (! myBranchUrls.contains(url)) {
        return true;
      }
    }

    return false;
  }

  private class MyBranchByNameSearcher implements UrlListener {
    private final String myName;
    private String myUrl;

    public MyBranchByNameSearcher(final String name) {
      myName = name;
    }

    public boolean accept(final String url) throws SVNException {
      if (myBranchUrls.contains(url)) {
        // do not take into account urls of groups
        return false;
      }
      if (myName.equals(SVNPathUtil.tail(url))) {
        myUrl = url;
        return true;
      }
      return false;
    }

    public String getUrl() {
      return myUrl;
    }
  }
}
