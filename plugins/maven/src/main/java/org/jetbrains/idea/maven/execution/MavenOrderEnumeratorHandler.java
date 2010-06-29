/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;

/**
 * @author nik
 */
public class MavenOrderEnumeratorHandler extends OrderEnumerationHandler {
  @Override
  public boolean isApplicable(@NotNull Project project) {
    return MavenProjectsManager.getInstance(project).isMavenizedProject();
  }

  @Override
  public boolean isApplicable(@NotNull Module module) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
    return manager.isMavenizedModule(module);
  }

  @Override
  public boolean shouldProcessRecursively(@NotNull ModuleOrderEntry dependency) {
    return false;
  }

  @Override
  public boolean addCustomOutput(@NotNull ModuleOrderEntry orderEntry,
                                 boolean productionOnly,
                                 @NotNull List<VirtualFile> result) {
    final Module ownerModule = orderEntry.getOwnerModule();
    final Module depModule = orderEntry.getModule();
    if (depModule == null) return false;

    final MavenProjectsManager manager = MavenProjectsManager.getInstance(ownerModule.getProject());
    MavenProject project = manager.findProject(ownerModule);
    MavenProject depProject = manager.findProject(depModule);
    if (project == null || depProject == null) {
      return false;
    }

    for (MavenArtifact each : project.findDependencies(depProject)) {

      if (productionOnly && MavenConstants.SCOPE_PROVIDEED.equals(each.getScope())) continue;
      if (productionOnly && MavenConstants.SCOPE_TEST.equals(each.getScope())) continue;

      boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(each.getType()) || "tests".equals(each.getClassifier());
      addOutput(depModule, isTestJar, result);
    }
    return true;
  }

  private static void addOutput(Module module, boolean tests, List<VirtualFile> result) {
    CompilerModuleExtension ex = CompilerModuleExtension.getInstance(module);
    if (ex == null) return;

    VirtualFile output = tests ? ex.getCompilerOutputPathForTests() : ex.getCompilerOutputPath();
    if (output != null) {
      result.add(output);
    }
  }
}
