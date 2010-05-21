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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// synch is here
public class NewRootBunch implements SvnBranchConfigManager {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.NewRootBunch");
  private final Object myLock = new Object();
  private final Project myProject;
  private final Map<VirtualFile, InfoStorage<SvnBranchConfigurationNew>> myMap;

  public NewRootBunch(final Project project) {
    myProject = project;
    myMap = new HashMap<VirtualFile, InfoStorage<SvnBranchConfigurationNew>>();
  }

  public void updateForRoot(@NotNull final VirtualFile root, @NotNull final InfoStorage<SvnBranchConfigurationNew> config,
                            @Nullable final PairConsumer<SvnBranchConfigurationNew, SvnBranchConfigurationNew> callbackOnUpdate) {
    synchronized (myLock) {
      final InfoStorage<SvnBranchConfigurationNew> existing = myMap.get(root);
      if (existing == null) {
        myMap.put(root, config);
        if (callbackOnUpdate != null) {
          callbackOnUpdate.consume(null, config.getValue());
        }
      } else {
        existing.accept(config, callbackOnUpdate);
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
        ApplicationManager.getApplication().executeOnPooledThread(new DefaultBranchConfigInitializer(myProject, this, root));
      } else {
        result = value.getValue();
      }
      return result;
    }
  }

  public void reloadBranches(@NotNull final VirtualFile root, @NotNull final String branchParentUrl,
                             final Consumer<List<SvnBranchItem>> callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new BranchesLoadRunnable(myProject, this, branchParentUrl,
                                                                                       InfoReliability.setByUser, root, callback));
  }

  @Nullable
  @CalledInBackground
  public SVNURL getWorkingBranchWithReload(final SVNURL svnurl, final VirtualFile root) {
    final Ref<SVNURL> result = new Ref<SVNURL>();
    try {
      final SvnBranchConfigurationNew configuration = myMap.get(root).getValue();
      final String group = configuration.getGroupToLoadToReachUrl(svnurl);
      final Runnable runnable = new Runnable() {
        public void run() {
          final SvnBranchConfigurationNew reloadedConfiguration = myMap.get(root).getValue();
          try {
            result.set(reloadedConfiguration.getWorkingBranch(svnurl));
          }
          catch (SVNException e) {
            //
          }
        }
      };

      if (group == null) {
        runnable.run();
      } else {
        new BranchesLoadRunnable(myProject, this, group, InfoReliability.setByUser, root,
                                 new Consumer<List<SvnBranchItem>>() {
                                   public void consume(List<SvnBranchItem> svnBranchItems) {
                                     runnable.run();
                                   }
                                 }).run();
      }
    }
    catch (SVNException e) {
      //
    }
    return result.get();
  }

  public static class BranchesLoadRunnable implements Runnable {
    private final Project myProject;
    private final SvnBranchConfigManager myBunch;
    private final VirtualFile myRoot;
    @Nullable
    private final Consumer<List<SvnBranchItem>> myCallback;
    private final String myUrl;
    private final InfoReliability myInfoReliability;

    public BranchesLoadRunnable(final Project project, final SvnBranchConfigManager bunch, final String url, final InfoReliability infoReliability,
                                 final VirtualFile root, @Nullable final Consumer<List<SvnBranchItem>> callback) {
      myProject = project;
      myBunch = bunch;
      myUrl = url;
      myInfoReliability = infoReliability;
      myRoot = root;
      myCallback = callback;
    }

    public void run() {
      boolean callbackCalled = false;
      try {
        final List<SvnBranchItem> items = BranchesLoader.loadBranches(myProject, myUrl);
        myBunch.updateBranches(myRoot, myUrl, new InfoStorage<List<SvnBranchItem>>(items, myInfoReliability));
        if (myCallback != null) {
          myCallback.consume(items);
          callbackCalled = true;
        }
      }
      catch (SVNException e) {
        // already logged inside
        if (InfoReliability.setByUser.equals(myInfoReliability)) {
          ChangesViewBalloonProblemNotifier.showMe(myProject, "Branches load error: " + e.getMessage(), MessageType.ERROR);
        }
      } finally {
        // callback must be called by contract
        if (myCallback != null && (! callbackCalled)) {
          myCallback.consume(Collections.<SvnBranchItem>emptyList());
        }
      }
    }
  }

  private static class DefaultBranchConfigInitializer implements Runnable {
    private final Project myProject;
    private final SvnBranchConfigManager myBunch;
    private final VirtualFile myRoot;

    private DefaultBranchConfigInitializer(final Project project, final SvnBranchConfigManager bunch, final VirtualFile root) {
      myProject = project;
      myRoot = root;
      myBunch = bunch;
    }

    public void run() {
      final SvnBranchConfigurationNew result = DefaultConfigLoader.loadDefaultConfiguration(myProject, myRoot);
      if (result != null) {
        final Application application = ApplicationManager.getApplication();
        for (String url : result.getBranchUrls()) {
          application.executeOnPooledThread(new BranchesLoadRunnable(myProject, myBunch, url, InfoReliability.defaultValues, myRoot, null));
        }
        myBunch.updateForRoot(myRoot, new InfoStorage<SvnBranchConfigurationNew>(result, InfoReliability.defaultValues), null);
      }
    }
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
