// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.ProgressManagerQueue;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.*;

// synch is here
public class NewRootBunch {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.NewRootBunch");
  private final Object myLock = new Object();
  private final Project myProject;
  private final ProgressManagerQueue myBranchesLoader;
  private final Map<VirtualFile, InfoStorage<SvnBranchConfigurationNew>> myMap;

  public NewRootBunch(final Project project, ProgressManagerQueue branchesLoader) {
    myProject = project;
    myBranchesLoader = branchesLoader;
    myMap = new HashMap<>();
  }

  public void updateForRoot(@NotNull final VirtualFile root,
                            @NotNull final InfoStorage<SvnBranchConfigurationNew> config,
                            boolean reload) {
    synchronized (myLock) {
      final SvnBranchConfigurationNew previous;
      boolean override;
      final InfoStorage<SvnBranchConfigurationNew> existing = myMap.get(root);

      if (existing == null) {
        previous = null;
        override = true;
        myMap.put(root, config);
      }
      else {
        previous = existing.getValue();
        override = existing.accept(config);
      }

      if (reload && override) {
        myBranchesLoader.run(() -> reloadBranches(root, previous, config.getValue()));
      }
    }
  }

  public void updateBranches(@NotNull final VirtualFile root, @NotNull final String branchesParent,
                             @NotNull final InfoStorage<List<SvnBranchItem>> items) {
    synchronized (myLock) {
      final InfoStorage<SvnBranchConfigurationNew> existing = myMap.get(root);
      if (existing == null) {
        LOG.info("cannot update branches, branches parent not found: " + branchesParent);
      } else {
        existing.getValue().updateBranch(branchesParent, items);
      }
    }
  }

  @NotNull
  public SvnBranchConfigurationNew getConfig(@NotNull final VirtualFile root) {
    synchronized (myLock) {
      final InfoStorage<SvnBranchConfigurationNew> value = myMap.get(root);
      final SvnBranchConfigurationNew result;
      if (value == null) {
        result = new SvnBranchConfigurationNew();
        myMap.put(root, new InfoStorage<>(result, InfoReliability.empty));
        myBranchesLoader.run(new DefaultBranchConfigInitializer(myProject, this, root));
      }
      else {
        result = value.getValue();
      }
      return result;
    }
  }

  public void reloadBranchesAsync(@NotNull final VirtualFile root,
                                  @NotNull final String branchLocation,
                                  @NotNull final InfoReliability reliability) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> reloadBranches(root, branchLocation, reliability, true));
  }

  public void reloadBranches(@NotNull VirtualFile root, @Nullable SvnBranchConfigurationNew prev, @NotNull SvnBranchConfigurationNew next) {
    final Set<String> oldUrls = (prev == null) ? Collections.emptySet() : new HashSet<>(prev.getBranchUrls());
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (!vcs.isVcsBackgroundOperationsAllowed(root)) return;

    for (String newBranchUrl : next.getBranchUrls()) {
      // check if cancel had been put
      if (!vcs.isVcsBackgroundOperationsAllowed(root)) return;
      if (!oldUrls.contains(newBranchUrl)) {
        reloadBranches(root, newBranchUrl, InfoReliability.defaultValues, true);
      }
    }
  }

  public void reloadBranches(@NotNull VirtualFile root,
                             @NotNull String branchLocation,
                             @NotNull InfoReliability reliability,
                             boolean passive) {
    new BranchesLoader(myProject, this, branchLocation, reliability, root, passive).run();
  }

  @Nullable
  @CalledInBackground
  public Url getWorkingBranch(@NotNull Url svnurl, @NotNull VirtualFile root) {
    Url result;

    try {
      result = myMap.get(root).getValue().getWorkingBranch(svnurl);
    }
    catch (SvnBindException e) {
      result = null;
    }

    return result;
  }

  public Map<VirtualFile, SvnBranchConfigurationNew> getMapCopy() {
    synchronized (myLock) {
      final Map<VirtualFile, SvnBranchConfigurationNew> result = new HashMap<>();
      for (VirtualFile vf : myMap.keySet()) {
        result.put(vf, myMap.get(vf).getValue());
      }
      return result;
    }
  }
}