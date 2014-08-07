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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManagerQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    myMap = new HashMap<VirtualFile, InfoStorage<SvnBranchConfigurationNew>>();
  }

  public void updateForRoot(@NotNull final VirtualFile root, @NotNull final InfoStorage<SvnBranchConfigurationNew> config,
                            @Nullable final PairConsumer<SvnBranchConfigurationNew, SvnBranchConfigurationNew> callbackOnUpdate) {
    synchronized (myLock) {
      SvnBranchConfigurationNew previous;
      boolean override;
      final InfoStorage<SvnBranchConfigurationNew> existing = myMap.get(root);

      if (existing == null) {
        previous = null;
        override = true;
        myMap.put(root, config);
      } else {
        previous = existing.getValue();
        override = existing.accept(config);
      }

      if (callbackOnUpdate != null && override) {
        callbackOnUpdate.consume(previous, config.getValue());
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
        myMap.put(root, new InfoStorage<SvnBranchConfigurationNew>(result, InfoReliability.empty));
        myBranchesLoader.run(new DefaultBranchConfigInitializer(myProject, this, root));
      } else {
        result = value.getValue();
      }
      return result;
    }
  }

  public void reloadBranches(@NotNull final VirtualFile root, @NotNull final String branchParentUrl) {
    ApplicationManager.getApplication()
      .executeOnPooledThread(new BranchesLoader(myProject, this, branchParentUrl, InfoReliability.setByUser, root, true));
  }

  @Nullable
  @CalledInBackground
  public SVNURL getWorkingBranchWithReload(final SVNURL svnurl, final VirtualFile root) {
    final Ref<SVNURL> result = new Ref<SVNURL>();
    try {
      final SvnBranchConfigurationNew configuration = myMap.get(root).getValue();
      final String group = configuration.getGroupToLoadToReachUrl(svnurl);

      if (group != null) {
        new BranchesLoader(myProject, this, group, InfoReliability.setByUser, root, true).run();
      }
      result.set(myMap.get(root).getValue().getWorkingBranch(svnurl));
    }
    catch (SVNException e) {
      //
    }
    return result.get();
  }

  public Map<VirtualFile, SvnBranchConfigurationNew> getMapCopy() {
    synchronized (myLock) {
      final Map<VirtualFile, SvnBranchConfigurationNew> result = new HashMap<VirtualFile, SvnBranchConfigurationNew>();
      for (VirtualFile vf : myMap.keySet()) {
        result.put(vf, myMap.get(vf).getValue());
      }
      return result;
    }
  }
}
