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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

@ApiStatus.Internal
public interface MavenProjectsProcessorTask {

  /**
   * @deprecated use {@link #perform(Project, MavenEmbeddersManager, ProgressIndicator)}
   */
  @Deprecated
  default void perform(@NotNull Project project,
                       MavenEmbeddersManager embeddersManager,
                       @Nullable MavenConsole console,
                       MavenProgressIndicator indicator) throws MavenProcessCanceledException {
  }

  default void perform(Project project, MavenEmbeddersManager embeddersManager, ProgressIndicator indicator) {
    var mavenIndicator = new MavenProgressIndicator(project, indicator, () -> MavenProjectsManager.getInstance(project).getSyncConsole());
    try {
      perform(project, embeddersManager, null, mavenIndicator);
    }
    catch (MavenProcessCanceledException e) {
      throw new ProcessCanceledException(e);
    }
  }
}
