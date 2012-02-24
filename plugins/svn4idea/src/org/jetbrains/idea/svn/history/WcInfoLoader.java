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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WcInfoLoader {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.WcInfoLoader");
  private final Project myProject;
  /**
   * filled when showing for selected location
   */
  private final RepositoryLocation myLocation;

  public WcInfoLoader(final Project project, final RepositoryLocation location) {
    myProject = project;
    myLocation = location;
  }

  public List<WCInfoWithBranches> loadRoots() {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (vcs == null) {
      return Collections.emptyList();
    }
    final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
    final List<WCInfo> wcInfoList = vcs.getAllWcInfos();

    final List<WCInfoWithBranches> result = new ArrayList<WCInfoWithBranches>();
    for (WCInfo info : wcInfoList) {
      final WCInfoWithBranches wcInfoWithBranches = createInfo(info, vcs, urlMapping);
      if (wcInfoWithBranches != null) {
        result.add(wcInfoWithBranches);
      }
    }
    return result;
  }

  @Nullable
  public WCInfoWithBranches reloadInfo(final WCInfoWithBranches info) {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (vcs == null) {
      return null;
    }
    final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
    final File file = new File(info.getPath());
    final RootUrlInfo rootInfo = urlMapping.getWcRootForFilePath(file);
    if (rootInfo == null) {
      return null;
    }
    final WCInfo wcInfo = new WCInfo(rootInfo.getIoFile().getAbsolutePath(), rootInfo.getAbsoluteUrlAsUrl(),
                             SvnFormatSelector.getWorkingCopyFormat(file), rootInfo.getRepositoryUrl(), SvnUtil.isWorkingCopyRoot(file),
                             rootInfo.getType(), SvnUtil.getDepth(vcs, file));
    return createInfo(wcInfo, vcs, urlMapping);
  }

  @Nullable
  private WCInfoWithBranches createInfo(final WCInfo info, final SvnVcs vcs, final SvnFileUrlMapping urlMapping) {
    if (! info.getFormat().supportsMergeInfo()) {
      return null;
    }

    final String url = info.getUrl().toString();
    if ((myLocation != null) && (! myLocation.toPresentableString().startsWith(url)) &&
        (! url.startsWith(myLocation.toPresentableString()))) {
      return null;
    }
    if (!SvnUtil.checkRepositoryVersion15(vcs, url)) {
      return null;
    }

    // check of WC version
    final RootUrlInfo rootForUrl = urlMapping.getWcRootForUrl(url);
    if (rootForUrl == null) {
      return null;
    }
    final VirtualFile root = rootForUrl.getRoot();
    final VirtualFile wcRoot = rootForUrl.getVirtualFile();
    if (wcRoot == null) {
      return null;
    }
    final SvnBranchConfigurationNew configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(myProject).get(wcRoot);
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
    if (configuration == null) {
      return null;
    }

    final List<WCInfoWithBranches.Branch> items = new ArrayList<WCInfoWithBranches.Branch>();
    final String branchRoot = createBranchesList(url, configuration, items);
    return new WCInfoWithBranches(info.getPath(), info.getUrl(), info.getFormat(),
                                                                         info.getRepositoryRoot(), info.isIsWcRoot(), items, root,
                                                                         branchRoot, info.getType(), info.getStickyDepth());
  }

  private static String createBranchesList(final String url, final SvnBranchConfigurationNew configuration,
                                                             final List<WCInfoWithBranches.Branch> items) {
    String result = null;
    final String trunkUrl = configuration.getTrunkUrl();
    if ((trunkUrl != null) && (! SVNPathUtil.isAncestor(trunkUrl, url))) {
      items.add(new WCInfoWithBranches.Branch(trunkUrl));
    } else if (trunkUrl != null) {
      result = trunkUrl;
    }
    for(String branchUrl: configuration.getBranchUrls()) {
      for (SvnBranchItem branchItem : configuration.getBranches(branchUrl)) {
        if (! SVNPathUtil.isAncestor(branchItem.getUrl(), url)) {
          items.add(new WCInfoWithBranches.Branch(branchItem.getUrl()));
        } else {
          result = branchItem.getUrl();
        }
      }
    }

    Collections.sort(items, new Comparator<WCInfoWithBranches.Branch>() {
      public int compare(final WCInfoWithBranches.Branch o1, final WCInfoWithBranches.Branch o2) {
        return Comparing.compare(o1.getUrl(), o2.getUrl());
      }
    });
    return result;
  }
}
