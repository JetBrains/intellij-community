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
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorEmptyTask extends MavenProjectsProcessorBasicTask {
  public MavenProjectsProcessorEmptyTask(MavenProject project) {
    super(project, null);
  }

  @Override
  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MavenProjectsProcessorBasicTask
           && myMavenProject.equals(((MavenProjectsProcessorBasicTask)o).myMavenProject);
  }
}
