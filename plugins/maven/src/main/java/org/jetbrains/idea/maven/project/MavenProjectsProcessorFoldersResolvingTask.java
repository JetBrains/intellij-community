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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenProjectsProcessorFoldersResolvingTask implements MavenProjectsProcessorTask {
  @NotNull private final Collection<MavenProject> myMavenProjects;
  @NotNull private final MavenImportingSettings myImportingSettings;
  @NotNull private final MavenProjectsTree myTree;
  @Nullable private final Runnable myOnCompletion;

  public MavenProjectsProcessorFoldersResolvingTask(@NotNull Collection<MavenProject> mavenProjects,
                                                    @NotNull MavenImportingSettings importingSettings,
                                                    @NotNull MavenProjectsTree tree,
                                                    @Nullable Runnable onCompletion) {
    myMavenProjects = mavenProjects;
    myImportingSettings = importingSettings;
    myTree = tree;
    myOnCompletion = onCompletion;
  }

  @Override
  @RequiresBlockingContext
  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    var resolver = new MavenFolderResolver();
    resolver.resolveFolders(myMavenProjects, myTree, myImportingSettings, embeddersManager, console, indicator);

    //actually a fix for https://youtrack.jetbrains.com/issue/IDEA-286455 to be rewritten, see IDEA-294209
    MavenUtil.restartMavenConnectors(project, false, c -> {
      Sdk sdk = c.getJdk();
      String version = sdk.getVersionString();
      if (version == null) return false;
      if (JavaVersion.parse(version).isAtLeast(17)) return true;
      return false;
    });
    if (myOnCompletion != null) myOnCompletion.run();
  }
}
