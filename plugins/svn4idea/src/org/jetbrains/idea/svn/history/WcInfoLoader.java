package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.*;

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
                             SvnFormatSelector.getWorkingCopyFormat(file), rootInfo.getRepositoryUrl(), SvnUtil.isWorkingCopyRoot(file));
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
    final SvnBranchConfiguration configuration;
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
                                                                         branchRoot);
  }

  private String createBranchesList(final String url, final SvnBranchConfiguration configuration,
                                                             final List<WCInfoWithBranches.Branch> items) {
    String result = null;
    final String trunkUrl = configuration.getTrunkUrl();
    if ((trunkUrl != null) && (! SVNPathUtil.isAncestor(trunkUrl, url))) {
      items.add(new WCInfoWithBranches.Branch(trunkUrl));
    } else if (trunkUrl != null) {
      result = trunkUrl;
    }
    final Map<String,List<SvnBranchItem>> branchMap = configuration.getLoadedBranchMap(myProject);
    for (Map.Entry<String, List<SvnBranchItem>> entry : branchMap.entrySet()) {
      for (SvnBranchItem branchItem : entry.getValue()) {
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
