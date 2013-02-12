/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

/**
 * @author Sergey Evdokimov
 */
public class MavenCompilerConfigurer extends MavenModuleConfigurer {
  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @Nullable Module module) {
    if (module == null) return;

    String targetLevel = mavenProject.getTargetLevel();
    if (targetLevel != null) {
      CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
      configuration.setBytecodeTargetLevel(module, targetLevel);
    }

    VirtualFile directoryFile = mavenProject.getDirectoryFile();

    // Exclude src/main/archetype-resources
    VirtualFile archetypeResourcesDir = VfsUtil.findRelativeFile(directoryFile, "src", "main", "resources", "archetype-resources");

    if (archetypeResourcesDir != null) {
      CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);

      if (!compilerConfiguration.isExcludedFromCompilation(archetypeResourcesDir)) {
        ExcludedEntriesConfiguration cfg = compilerConfiguration.getExcludedEntriesConfiguration();

        cfg.addExcludeEntryDescription(new ExcludeEntryDescription(archetypeResourcesDir, true, false, project));
      }
    }
  }
}
