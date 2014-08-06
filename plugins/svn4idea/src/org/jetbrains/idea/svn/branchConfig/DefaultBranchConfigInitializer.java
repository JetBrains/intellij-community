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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
* @author Konstantin Kolosovsky.
*/
class DefaultBranchConfigInitializer implements Runnable {
  private final Project myProject;
  private final NewRootBunch myBunch;
  private final VirtualFile myRoot;

  DefaultBranchConfigInitializer(final Project project, final NewRootBunch bunch, final VirtualFile root) {
    myProject = project;
    myRoot = root;
    myBunch = bunch;
  }

  public void run() {
    final SvnBranchConfigurationNew result = DefaultConfigLoader.loadDefaultConfiguration(myProject, myRoot);
    if (result != null) {
      final Application application = ApplicationManager.getApplication();
      for (String url : result.getBranchUrls()) {
        application.executeOnPooledThread(new BranchesLoadRunnable(myProject, myBunch, url, InfoReliability.defaultValues, myRoot, null,
                                                                   true));
      }
      myBunch.updateForRoot(myRoot, new InfoStorage<SvnBranchConfigurationNew>(result, InfoReliability.defaultValues), null);
    }
  }
}
