/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.ProgressManagerQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
@State(name = "SvnBranchConfigurationManager")
public class SvnBranchConfigurationManager implements PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager");
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final SvnLoadedBranchesStorage myStorage;
  private final ProgressManagerQueue myBranchesLoader;
  private boolean myIsInitialized;

  public SvnBranchConfigurationManager(final Project project,
                                       final ProjectLevelVcsManager vcsManager,
                                       final SvnLoadedBranchesStorage storage) {
    myProject = project;
    myVcsManager = vcsManager;
    myStorage = storage;
    myBranchesLoader = new ProgressManagerQueue(myProject, "Subversion Branches Preloader");
    // TODO: Seems that ProgressManagerQueue is not suitable here at least for some branches loading tasks. For instance,
    // TODO: for DefaultConfigLoader it would be better to run modal cancellable task - so branches structure could be detected and
    // TODO: shown in dialog. Currently when "Configure Branches" is invoked for the first time - no branches are shown.
    // TODO: If "Cancel" is pressed and "Configure Branches" invoked once again - already detected (in background) branches are shown.
    ((ProjectLevelVcsManagerImpl)vcsManager)
      .addInitializationRequest(VcsInitObject.BRANCHES, () -> ApplicationManager.getApplication().runReadAction(() -> {
        if (myProject.isDisposed()) return;
        myBranchesLoader.start();
      }));
    myBunch = new NewRootBunch(project, myBranchesLoader);
  }

  public static SvnBranchConfigurationManager getInstance(@NotNull Project project) {
    SvnBranchConfigurationManager result = ServiceManager.getService(project, SvnBranchConfigurationManager.class);

    if (result != null) {
      result.initialize();
    }

    return result;
  }

  public static class ConfigurationBean {
    public Map<String, SvnBranchConfiguration> myConfigurationMap = new TreeMap<>();
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    public Long myVersion;
  }

  public Long getSupportValue() {
    return myConfigurationBean.myVersion;
  }

  private ConfigurationBean myConfigurationBean = new ConfigurationBean();
  @NotNull private final NewRootBunch myBunch;

  @NotNull
  public SvnBranchConfigurationNew get(@NotNull final VirtualFile vcsRoot) {
    return myBunch.getConfig(vcsRoot);
  }

  @NotNull
  public NewRootBunch getSvnBranchConfigManager() {
    return myBunch;
  }

  public void setConfiguration(final VirtualFile vcsRoot, final SvnBranchConfigurationNew configuration) {
    myBunch.updateForRoot(vcsRoot, new InfoStorage<>(configuration, InfoReliability.setByUser), true);

    SvnBranchMapperManager.getInstance().notifyBranchesChanged(myProject, vcsRoot, configuration);

    BackgroundTaskUtil.syncPublisher(myProject, VcsConfigurationChangeListener.BRANCHES_CHANGED).execute(myProject, vcsRoot);
  }

  public ConfigurationBean getState() {
    final ConfigurationBean result = new ConfigurationBean();
    result.myVersion = myConfigurationBean.myVersion;
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));

    for (VirtualFile root : myBunch.getMapCopy().keySet()) {
      final String key = root.getPath();
      final SvnBranchConfigurationNew configOrig = myBunch.getConfig(root);
      final SvnBranchConfiguration configuration =
        new SvnBranchConfiguration(configOrig.getTrunkUrl(), configOrig.getBranchUrls(), configOrig.isUserinfoInUrl());

      result.myConfigurationMap.put(key, helper.prepareForSerialization(configuration));
    }
    return result;
  }

  public void loadState(@NotNull ConfigurationBean object) {
    myConfigurationBean = object;
  }

  private synchronized void initialize() {
    if (!myIsInitialized) {
      myIsInitialized = true;

      preloadBranches(resolveAllBranchPoints());
    }
  }

  @NotNull
  private Set<Pair<VirtualFile, SvnBranchConfigurationNew>> resolveAllBranchPoints() {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));
    final Set<Pair<VirtualFile, SvnBranchConfigurationNew>> branchPointsToLoad = ContainerUtil.newHashSet();
    for (Map.Entry<String, SvnBranchConfiguration> entry : myConfigurationBean.myConfigurationMap.entrySet()) {
      final SvnBranchConfiguration configuration = entry.getValue();
      final VirtualFile root = lfs.refreshAndFindFileByIoFile(new File(entry.getKey()));
      if (root == null) {
        LOG.info("root not found: " + entry.getKey());
        continue;
      }

      final SvnBranchConfiguration configToConvert;
      if (configuration.isUserinfoInUrl()) {
        configToConvert = helper.afterDeserialization(entry.getKey(), configuration);
      }
      else {
        configToConvert = configuration;
      }
      final SvnBranchConfigurationNew newConfig = new SvnBranchConfigurationNew();
      newConfig.setTrunkUrl(configToConvert.getTrunkUrl());
      newConfig.setUserinfoInUrl(configToConvert.isUserinfoInUrl());
      for (String branchUrl : configToConvert.getBranchUrls()) {
        List<SvnBranchItem> stored = getStored(branchUrl);
        if (stored != null && !stored.isEmpty()) {
          newConfig.addBranches(branchUrl, new InfoStorage<>(stored, InfoReliability.setByUser));
        }
        else {
          branchPointsToLoad.add(Pair.create(root, newConfig));
          newConfig.addBranches(branchUrl, new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
        }
      }

      myBunch.updateForRoot(root, new InfoStorage<>(newConfig, InfoReliability.setByUser), false);
    }
    return branchPointsToLoad;
  }

  private void preloadBranches(@NotNull final Collection<Pair<VirtualFile, SvnBranchConfigurationNew>> branchPoints) {
    ((ProjectLevelVcsManagerImpl)myVcsManager)
      .addInitializationRequest(VcsInitObject.BRANCHES, () -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          for (Pair<VirtualFile, SvnBranchConfigurationNew> pair : branchPoints) {
            myBunch.reloadBranches(pair.getFirst(), null, pair.getSecond());
          }
        }
        catch (ProcessCanceledException e) {
          //
        }
      }));
  }

  private List<SvnBranchItem> getStored(String branchUrl) {
    Collection<SvnBranchItem> collection = myStorage.get(branchUrl);
    if (collection == null) return null;
    final List<SvnBranchItem> items = new ArrayList<>(collection);
    Collections.sort(items);
    return items;
  }
}
