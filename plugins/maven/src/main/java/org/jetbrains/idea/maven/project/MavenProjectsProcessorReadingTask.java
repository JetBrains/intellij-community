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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenWrapperDownloader;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Path;

public class MavenProjectsProcessorReadingTask implements MavenProjectsProcessorTask {
  private final boolean myForce;
  private final MavenProjectsTree myTree;
  private final MavenGeneralSettings mySettings;
  @Nullable private final Runnable myOnCompletion;

  public MavenProjectsProcessorReadingTask(boolean force,
                                           MavenProjectsTree tree,
                                           MavenGeneralSettings settings,
                                           @Nullable Runnable onCompletion) {
    myForce = force;
    myTree = tree;
    mySettings = settings;
    myOnCompletion = onCompletion;
  }

  @Override
  public void perform(Project project,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProgressIndicator indicator) throws MavenProcessCanceledException {
    try {
      checkOrInstallMavenWrapper(project);
      myTree.updateAll(myForce, mySettings, indicator);

      mySettings.updateFromMavenConfig(myTree.getRootProjectsFiles());
    }
    finally {
      if (myOnCompletion != null) myOnCompletion.run();
    }
  }

  private void checkOrInstallMavenWrapper(Project project) {
    if (myTree.getExistingManagedFiles().size() == 1) {
      Path baseDir = MavenUtil.getBaseDir(myTree.getExistingManagedFiles().get(0));
      if (MavenUtil.isWrapper(mySettings)) {
        MavenWrapperDownloader.checkOrInstallForSync(project, baseDir.toString());
      }
    }
  }
}