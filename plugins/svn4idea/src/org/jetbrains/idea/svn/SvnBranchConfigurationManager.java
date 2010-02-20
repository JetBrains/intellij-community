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

package org.jetbrains.idea.svn;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.branchConfig.*;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
@State(
  name = "SvnBranchConfigurationManager",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )}
)
public class SvnBranchConfigurationManager implements PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnBranchConfigurationManager");
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public SvnBranchConfigurationManager(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myBunch = new NewRootBunch(project);
  }

  public static SvnBranchConfigurationManager getInstance(final Project project) {
    return ServiceManager.getService(project, SvnBranchConfigurationManager.class);
  }

  public static class ConfigurationBean {
    public Map<String, SvnBranchConfiguration> myConfigurationMap = new HashMap<String, SvnBranchConfiguration>();
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    public Long myVersion;
    public boolean mySupportsUserInfoFilter;
  }

  public Long getSupportValue() {
    return myConfigurationBean.myVersion;
  }

  private ConfigurationBean myConfigurationBean = new ConfigurationBean();
  private final SvnBranchConfigManager myBunch;

  public SvnBranchConfigurationNew get(@NotNull final VirtualFile vcsRoot) throws VcsException {
    return myBunch.getConfig(vcsRoot);
  }

  public SvnBranchConfigManager getSvnBranchConfigManager() {
    return myBunch;
  }

  public void setConfiguration(final VirtualFile vcsRoot, final SvnBranchConfigurationNew configuration) {
    myBunch.updateForRoot(vcsRoot, new InfoStorage<SvnBranchConfigurationNew>(configuration, InfoReliability.setByUser),
                          new BranchesPreloader(myProject, myBunch, vcsRoot));

    SvnBranchMapperManager.getInstance().notifyBranchesChanged(myProject, vcsRoot, configuration);

    final MessageBus messageBus = myProject.getMessageBus();
    messageBus.syncPublisher(VcsConfigurationChangeListener.BRANCHES_CHANGED).execute(myProject, vcsRoot);
  }

  public ConfigurationBean getState() {
    final ConfigurationBean result = new ConfigurationBean();
    result.myVersion = myConfigurationBean.myVersion;
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));

    for (VirtualFile root : myBunch.getMapCopy().keySet()) {
      final String key = root.getPath();
      final SvnBranchConfigurationNew configOrig = myBunch.getConfig(root);
      final SvnBranchConfiguration configuration = new SvnBranchConfiguration();
      configuration.setTrunkUrl(configOrig.getTrunkUrl());
      configuration.setUserinfoInUrl(configOrig.isUserinfoInUrl());
      configuration.setBranchUrls(configOrig.getBranchUrls());
      final HashMap<String, List<SvnBranchItem>> map = new HashMap<String, List<SvnBranchItem>>();
      final Map<String, InfoStorage<List<SvnBranchItem>>> origMap = configOrig.getBranchMap();
      for (String origKey : origMap.keySet()) {
        map.put(origKey, origMap.get(origKey).getValue());
      }
      configuration.setBranchMap(map);
      result.myConfigurationMap.put(key, helper.prepareForSerialization(configuration));
    }
    result.mySupportsUserInfoFilter = true;
    return result;
  }

  private static class BranchesPreloader implements PairConsumer<SvnBranchConfigurationNew, SvnBranchConfigurationNew> {
    private final Project myProject;
    private final VirtualFile myRoot;
    private final SvnBranchConfigManager myBunch;
    private boolean myAll;

    public BranchesPreloader(Project project, @NotNull final SvnBranchConfigManager bunch, VirtualFile root) {
      myBunch = bunch;
      myProject = project;
      myRoot = root;
    }

    public void consume(final SvnBranchConfigurationNew prev, final SvnBranchConfigurationNew next) {
      final Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          loadImpl(prev, next);
        }
      });
    }

    protected void loadImpl(final SvnBranchConfigurationNew prev, final SvnBranchConfigurationNew next) {
      final Set<String> oldUrls = (prev == null) ? Collections.<String>emptySet() : new HashSet<String>(prev.getBranchUrls());
      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      if (! vcs.isVcsBackgroundOperationsAllowed(myRoot)) return;

      for (String newBranchUrl : next.getBranchUrls()) {
        // check if cancel had been put 
        if (! vcs.isVcsBackgroundOperationsAllowed(myRoot)) return;
        if (myAll || (! oldUrls.contains(newBranchUrl))) {
          new NewRootBunch.BranchesLoadRunnable(myProject, myBunch, newBranchUrl, InfoReliability.defaultValues, myRoot, null).run();
        }
      }
    }

    public void setAll(boolean all) {
      myAll = all;
    }
  }

  public void loadState(final ConfigurationBean object) {
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));
    final Map<String, SvnBranchConfiguration> map = object.myConfigurationMap;
    final Map<String, SvnBranchConfiguration> newMap = new HashMap<String, SvnBranchConfiguration>(map.size(), 1);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();

    final Set<Pair<VirtualFile, SvnBranchConfigurationNew>> whatToInit = new HashSet<Pair<VirtualFile, SvnBranchConfigurationNew>>();
    for (Map.Entry<String, SvnBranchConfiguration> entry : map.entrySet()) {
      final SvnBranchConfiguration configuration = entry.getValue();
      final VirtualFile root = lfs.refreshAndFindFileByIoFile(new File(entry.getKey()));
      if (root == null) {
        LOG.info("root not found: " + entry.getKey());
        continue;
      }

      final SvnBranchConfiguration configToConvert;
      if ((! myConfigurationBean.mySupportsUserInfoFilter) || configuration.isUserinfoInUrl()) {
        configToConvert = helper.afterDeserialization(entry.getKey(), configuration);
      } else {
        configToConvert = configuration;
      }
      final SvnBranchConfigurationNew newConfig = new SvnBranchConfigurationNew();
      newConfig.setTrunkUrl(configToConvert.getTrunkUrl());
      newConfig.setUserinfoInUrl(configToConvert.isUserinfoInUrl());
      final Map<String, List<SvnBranchItem>> oldMap = configToConvert.getBranchMap();
      for (String branchUrl : configToConvert.getBranchUrls()) {
        List<SvnBranchItem> items = oldMap.get(branchUrl);
        items = ((items == null) || (items.isEmpty())) ? new ArrayList<SvnBranchItem>() : items;
        whatToInit.add(new Pair<VirtualFile, SvnBranchConfigurationNew>(root, newConfig));
        newConfig.addBranches(branchUrl, new InfoStorage<List<SvnBranchItem>>(items,
            (items.isEmpty()) ? InfoReliability.defaultValues : InfoReliability.setByUser));
      }

      myBunch.updateForRoot(root, new InfoStorage<SvnBranchConfigurationNew>(newConfig, InfoReliability.setByUser), null);
    }
    ((ProjectLevelVcsManagerImpl) myVcsManager).addInitializationRequest(VcsInitObject.BRANCHES, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            try {
              for (Pair<VirtualFile, SvnBranchConfigurationNew> pair : whatToInit) {
                final BranchesPreloader branchesPreloader = new BranchesPreloader(myProject, myBunch, pair.getFirst());
                branchesPreloader.setAll(true);
                branchesPreloader.loadImpl(null, pair.getSecond());
              }
            }
            catch (ProcessCanceledException e) {
              //
            }
          }
        });
      }
    });
    object.myConfigurationMap.clear();
    object.myConfigurationMap.putAll(newMap);
    myConfigurationBean = object;
  }

  private static class UrlSerializationHelper {
    private final SvnVcs myVcs;

    private UrlSerializationHelper(final SvnVcs vcs) {
      myVcs = vcs;
    }

    public SvnBranchConfiguration prepareForSerialization(final SvnBranchConfiguration configuration) {
      final Ref<Boolean> withUserInfo = new Ref<Boolean>();
      final String trunkUrl = serializeUrl(configuration.getTrunkUrl(), withUserInfo);

      if (Boolean.FALSE.equals(withUserInfo.get())) {
        return configuration;
      }

      final List<String> branches = configuration.getBranchUrls();
      final List<String> newBranchesList = new ArrayList<String>(branches.size());
      for (String s : branches) {
        newBranchesList.add(serializeUrl(s, withUserInfo));
      }

      final Map<String, List<SvnBranchItem>> map = configuration.getBranchMap();
      final Map<String, List<SvnBranchItem>> newMap = new HashMap<String, List<SvnBranchItem>>(map.size(), 1.0f);
      for (Map.Entry<String, List<SvnBranchItem>> entry : map.entrySet()) {
        final List<SvnBranchItem> items = entry.getValue();
        if (items != null) {
          final List<SvnBranchItem> newItems = new ArrayList<SvnBranchItem>();
          for (SvnBranchItem item : items) {
            newItems.add(new SvnBranchItem(serializeUrl(item.getUrl(), withUserInfo), new java.util.Date(item.getCreationDateMillis()),
                                           item.getRevision()));
          }
          newMap.put(serializeUrl(entry.getKey(), withUserInfo), newItems);
        }
      }

      final SvnBranchConfiguration result = new SvnBranchConfiguration();
      result.setTrunkUrl(trunkUrl);
      result.setBranchUrls(newBranchesList);
      result.setBranchMap(newMap);
      result.setUserinfoInUrl(withUserInfo.isNull() ? false : withUserInfo.get());
      return result;
    }

    public SvnBranchConfiguration afterDeserialization(final String path, final SvnBranchConfiguration configuration) {
      if (! configuration.isUserinfoInUrl()) {
        return configuration;
      }
      final String userInfo = getUserInfo(path);
      if (userInfo == null) {
        return configuration;
      }

      final String newTrunkUrl = deserializeUrl(configuration.getTrunkUrl(), userInfo);
      final List<String> branches = configuration.getBranchUrls();
      final List<String> newBranchesList = new ArrayList<String>(branches.size());
      for (String s : branches) {
        newBranchesList.add(deserializeUrl(s, userInfo));
      }

      final Map<String, List<SvnBranchItem>> map = configuration.getBranchMap();
      final Map<String, List<SvnBranchItem>> newMap = new HashMap<String, List<SvnBranchItem>>(map.size(), 1.0f);
      for (Map.Entry<String, List<SvnBranchItem>> entry : map.entrySet()) {
        final List<SvnBranchItem> items = entry.getValue();
        if (items != null) {
          final List<SvnBranchItem> newItems = new ArrayList<SvnBranchItem>();
          for (SvnBranchItem item : items) {
            newItems.add(new SvnBranchItem(deserializeUrl(item.getUrl(), userInfo), new java.util.Date(item.getCreationDateMillis()),
                                           item.getRevision()));
          }
          newMap.put(deserializeUrl(entry.getKey(), userInfo), newItems);
        }
      }

      final SvnBranchConfiguration result = new SvnBranchConfiguration();
      result.setTrunkUrl(newTrunkUrl);
      result.setBranchUrls(newBranchesList);
      result.setBranchMap(newMap);
      result.setUserinfoInUrl(userInfo != null && userInfo.length() > 0);
      return result;
    }

    private String serializeUrl(final String url, final Ref<Boolean> withUserInfo) {
      if (Boolean.FALSE.equals(withUserInfo.get())) {
        return url;
      }
      try {
        final SVNURL svnurl = SVNURL.parseURIEncoded(url);
        if (withUserInfo.isNull()) {
          final String userInfo = svnurl.getUserInfo();
          withUserInfo.set((userInfo != null) && (userInfo.length() > 0));
        }
        if (withUserInfo.get()) {
          return SVNURL.create(svnurl.getProtocol(), null, svnurl.getHost(), svnurl.getPort(), svnurl.getURIEncodedPath(), true).toString();
        }
      }
      catch (SVNException e) {
        //
      }
      return url;
    }

    @Nullable
    private String getUserInfo(final String path) {
      final SVNURL svnurl = myVcs.getSvnFileUrlMapping().getUrlForFile(new File(path));
      return svnurl != null ? svnurl.getUserInfo() : null;
    }

    private String deserializeUrl(final String url, final String userInfo) {
      try {
        final SVNURL svnurl = SVNURL.parseURIEncoded(url);
        return SVNURL.create(svnurl.getProtocol(), userInfo, svnurl.getHost(), svnurl.getPort(), svnurl.getURIEncodedPath(), true).toString();
      } catch (SVNException e) {
        return url;
      }
    }
  }
}
