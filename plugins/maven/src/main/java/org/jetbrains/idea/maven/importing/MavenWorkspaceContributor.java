/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemWorkspaceContributor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author Vladislav.Soroka
 */
public class MavenWorkspaceContributor implements ExternalSystemWorkspaceContributor {
  @Nullable
  @Override
  public ProjectCoordinate findProjectId(Module module) {
    if (!ExternalSystemModulePropertyManager.getInstance(module).isMavenized()) {
      return null;
    }
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject != null) {
      MavenId mavenId = mavenProject.getMavenId();
      return new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion());
    }
    return null;
  }
}
