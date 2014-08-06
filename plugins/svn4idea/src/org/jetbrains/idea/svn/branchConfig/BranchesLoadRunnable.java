/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.util.List;

/**
* @author Konstantin Kolosovsky.
*/
public class BranchesLoadRunnable implements Runnable {
  private final Project myProject;
  private final NewRootBunch myBunch;
  private final VirtualFile myRoot;
  @Nullable
  private final Consumer<List<SvnBranchItem>> myCallback;
  private final String myUrl;
  private final InfoReliability myInfoReliability;
  private boolean myPassive;

  public BranchesLoadRunnable(final Project project,
                              final NewRootBunch bunch,
                              final String url,
                              final InfoReliability infoReliability,
                              final VirtualFile root,
                              @Nullable final Consumer<List<SvnBranchItem>> callback,
                              boolean passive) {
    myProject = project;
    myBunch = bunch;
    myUrl = url;
    myInfoReliability = infoReliability;
    myRoot = root;
    myCallback = callback;
    myPassive = passive;
  }

  public void run() {
    boolean callbackCalled = false;
    try {
      final List<SvnBranchItem> items = BranchesLoader.loadBranches(myProject, myUrl, myPassive);
      myBunch.updateBranches(myRoot, myUrl, new InfoStorage<List<SvnBranchItem>>(items, myInfoReliability));
      if (myCallback != null) {
        myCallback.consume(items);
        callbackCalled = true;
      }
    }
    catch (VcsException e) {
      showError(e);
    }
    catch (SVNException e) {
      showError(e);
    }
    finally {
      // callback must be called by contract
      if (myCallback != null && (! callbackCalled)) {
        myCallback.consume(null);
      }
    }
  }

  private void showError(Exception e) {
    // already logged inside
    if (InfoReliability.setByUser.equals(myInfoReliability)) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Branches load error: " + e.getMessage(), MessageType.ERROR);
    }
  }
}
