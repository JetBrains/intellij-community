// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public final class MavenModulePathMapper {
  public static void resolveModulePaths(Collection<MavenProject> projects,
                                        Map<MavenProject, Module> mavenProjectToModule,
                                        Map<MavenProject, String> mavenProjectToModuleName,
                                        Map<MavenProject, String> mavenProjectToModulePath,
                                        String dedicatedModuleDir) {
    for (MavenProject each : projects) {
      Module module = mavenProjectToModule.get(each);
      String path = getPath(mavenProjectToModuleName.get(each), each, dedicatedModuleDir, module);
      mavenProjectToModulePath.put(each, path);
    }
  }

  private static @NotNull @NonNls String getPath(@NotNull String moduleName,
                                                 @NotNull MavenProject each,
                                                 @Nullable String dedicatedModuleDir,
                                                 @Nullable Module module) {
    return module != null
           ? module.getModuleFilePath()
           : generateModulePath(each, moduleName, dedicatedModuleDir);
  }

  private static @NotNull String generateModulePath(MavenProject project,
                                                    String moduleName,
                                                    String dedicatedModuleDir) {
    String dir = StringUtil.isEmptyOrSpaces(dedicatedModuleDir)
                 ? project.getDirectory()
                 : dedicatedModuleDir;
    String fileName = moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
    return new File(dir, fileName).getPath();
  }
}
