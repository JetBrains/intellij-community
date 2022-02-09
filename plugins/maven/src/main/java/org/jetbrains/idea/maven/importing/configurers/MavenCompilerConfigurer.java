/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenDisposable;
import org.jetbrains.idea.maven.importing.MavenModelUtil;
import org.jetbrains.idea.maven.project.MavenProject;

import static org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider.TEST_SUFIX;

/**
 * @author Sergey Evdokimov
 */
public class MavenCompilerConfigurer extends MavenModuleConfigurer {
  
  public static final Key<Boolean> IGNORE_MAVEN_COMPILER_TARGET_KEY = Key.create("idea.maven.skip.compiler.target.level");

  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @NotNull Module module) {
    CompilerConfiguration configuration = CompilerConfiguration.getInstance(project);
    if (!Boolean.TRUE.equals(module.getUserData(IGNORE_MAVEN_COMPILER_TARGET_KEY))) {
      LanguageLevel level;
      if (module.getName().endsWith(TEST_SUFIX)) {
        level = MavenModelUtil.getTargetTestLanguageLevel(mavenProject);
        if (level == null) {
          level = MavenModelUtil.getTargetLanguageLevel(mavenProject);
        }
      } else {
        level = MavenModelUtil.getTargetLanguageLevel(mavenProject);
      }
       if (level == null) {
         level = MavenModelUtil.getDefaultLevel(mavenProject);
       }

      // default source and target settings of maven-compiler-plugin is 1.5, see details at http://maven.apache.org/plugins/maven-compiler-plugin!
      configuration.setBytecodeTargetLevel(module, level.toJavaVersion().toString());
    }
    module.putUserData(IGNORE_MAVEN_COMPILER_TARGET_KEY, Boolean.FALSE);

    // Exclude src/main/archetype-resources
    VirtualFile dir = VfsUtil.findRelativeFile(mavenProject.getDirectoryFile(), "src", "main", "resources", "archetype-resources");
    if (dir != null && !configuration.isExcludedFromCompilation(dir)) {
      ExcludesConfiguration cfg = configuration.getExcludedEntriesConfiguration();
      cfg.addExcludeEntryDescription(new ExcludeEntryDescription(dir, true, false, MavenDisposable.getInstance(project)));
    }
  }
}
