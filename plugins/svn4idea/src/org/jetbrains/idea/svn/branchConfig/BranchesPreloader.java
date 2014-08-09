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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
* @author Konstantin Kolosovsky.
*/
public class BranchesPreloader {
  private final Project myProject;
  private final VirtualFile myRoot;
  private final NewRootBunch myBunch;

  public BranchesPreloader(Project project, @NotNull final NewRootBunch bunch, VirtualFile root) {
    myBunch = bunch;
    myProject = project;
    myRoot = root;
  }

  public void loadImpl(final SvnBranchConfigurationNew prev, final SvnBranchConfigurationNew next) {
    final Set<String> oldUrls = (prev == null) ? Collections.<String>emptySet() : new HashSet<String>(prev.getBranchUrls());
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (! vcs.isVcsBackgroundOperationsAllowed(myRoot)) return;

    for (String newBranchUrl : next.getBranchUrls()) {
      // check if cancel had been put
      if (! vcs.isVcsBackgroundOperationsAllowed(myRoot)) return;
      if (!oldUrls.contains(newBranchUrl)) {
        new BranchesLoader(myProject, myBunch, newBranchUrl, InfoReliability.defaultValues, myRoot, true).run();
      }
    }
  }
}
