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
import java.util.function.Consumer;

public class MavenProjectsProcessorResolvingTask implements MavenProjectsProcessorTask {
  @NotNull private final Collection<MavenProject> myMavenProjects;
  @NotNull private final MavenGeneralSettings myGeneralSettings;
  @NotNull private final MavenProjectsTree myTree;
  @Nullable private final Consumer<MavenProjectResolver.MavenProjectResolutionResult> myOnCompletion;

  public MavenProjectsProcessorResolvingTask(@NotNull Collection<MavenProject> mavenProjects,
                                             @NotNull MavenGeneralSettings generalSettings,
                                             @NotNull MavenProjectsTree tree,
                                             @Nullable Consumer<MavenProjectResolver.MavenProjectResolutionResult> onCompletion) {
    myMavenProjects = mavenProjects;
    myGeneralSettings = generalSettings;
    myTree = tree;
    myOnCompletion = onCompletion;
  }

  @Override
  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    var resolver = MavenProjectResolver.getInstance(project);
    var result = resolver.resolve(myMavenProjects, myTree, myGeneralSettings, embeddersManager, console, indicator);
    if (myOnCompletion != null) myOnCompletion.accept(result);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myMavenProjects.equals(((MavenProjectsProcessorResolvingTask)o).myMavenProjects);
  }

  @Override
  public int hashCode() {
    return myMavenProjects.hashCode();
  }
}
