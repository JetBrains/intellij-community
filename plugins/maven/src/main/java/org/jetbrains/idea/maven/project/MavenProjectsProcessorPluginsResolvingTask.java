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
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;

public class MavenProjectsProcessorPluginsResolvingTask implements MavenProjectsProcessorTask {
  private final @NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> myMavenProjects;
  private final @NotNull MavenProjectResolver myResolver;

  public MavenProjectsProcessorPluginsResolvingTask(@NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> projects,
                                                    @NotNull MavenProjectResolver resolver) {
    myMavenProjects = projects;
    myResolver = resolver;
  }

  @Override
  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myResolver.resolvePlugins(myMavenProjects, embeddersManager, console, indicator, true, false);
  }
}
