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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsBatchProcessorBasicTask {
  @NotNull private final MavenGeneralSettings myGeneralSettings;
  @Nullable private final Runnable myOnCompletion;
  @NotNull private final ResolveContext myContext;

  public MavenProjectsProcessorResolvingTask(@NotNull Collection<MavenProject> projects,
                                             @NotNull MavenProjectsTree tree,
                                             @NotNull MavenGeneralSettings generalSettings,
                                             @Nullable Runnable onCompletion,
                                             @NotNull ResolveContext context) {
    super(projects, tree);
    myGeneralSettings = generalSettings;
    myOnCompletion = onCompletion;
    myContext = context;
  }

  @Override
  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myResolver.resolve(project, myMavenProjects, myGeneralSettings, embeddersManager, console, myContext, indicator);
    if (myOnCompletion != null) myOnCompletion.run();
  }
}
