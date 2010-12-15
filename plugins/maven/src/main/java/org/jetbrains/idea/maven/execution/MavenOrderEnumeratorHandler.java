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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.Collection;
import java.util.List;

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
  @NotNull
  public AddDependencyType shouldAddDependency(@NotNull OrderEntry orderEntry,
                                               boolean productionOnly,
                                               boolean runtimeOnly,
                                               boolean compileOnly) {
    Module ownerModule = orderEntry.getOwnerModule();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(ownerModule.getProject());

    MavenProject project = manager.findProject(ownerModule);
    if (project == null) return AddDependencyType.DEFAULT;

    if (orderEntry instanceof LibraryOrderEntry) {
      MavenArtifact artifact = MavenRootModelAdapter.findArtifact(project, ((LibraryOrderEntry)orderEntry).getLibrary());
      if (artifact == null) return AddDependencyType.DEFAULT;

      return shouldAddArtifact(artifact, productionOnly, runtimeOnly, compileOnly) ? AddDependencyType.ADD : AddDependencyType.DO_NOT_ADD;
    }
    else if (orderEntry instanceof ModuleOrderEntry) {
      Module depModule = ((ModuleOrderEntry)orderEntry).getModule();
      if (depModule == null) return AddDependencyType.DEFAULT;

      MavenProject depProject = manager.findProject(depModule);
      if (depProject == null) return AddDependencyType.DEFAULT;

      List<MavenArtifact> deps = project.findDependencies(depProject);

      for (MavenArtifact each : deps) {
        if (shouldAddArtifact(each, productionOnly, runtimeOnly, compileOnly)) return OrderEnumerationHandler.AddDependencyType.ADD;
      }
      return AddDependencyType.DO_NOT_ADD;
    }

    return AddDependencyType.DEFAULT;
  }

  private boolean shouldAddArtifact(MavenArtifact artifact, boolean productionOnly, boolean runtimeOnly, boolean compileOnly) {
    if (productionOnly) {
      String scope = artifact.getScope();
      if (compileOnly && MavenConstants.SCOPE_RUNTIME.endsWith(scope)) return false;
      if (runtimeOnly && MavenConstants.SCOPE_PROVIDEED.equals(scope)) return false;
      if (MavenConstants.SCOPE_TEST.equals(scope)) return false;
    }
    return true;
  }

  @Override
  public boolean shouldProcessRecursively(@NotNull ModuleOrderEntry dependency) {
    return false;
  }

  @Override
  public boolean addCustomOutput(@NotNull ModuleOrderEntry orderEntry,
                                 boolean productionOnly,
                                 boolean runtimeOnly,
                                 boolean compileOnly,
                                 @NotNull Collection<String> urls) {
    Module ownerModule = orderEntry.getOwnerModule();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(ownerModule.getProject());
    MavenProject project = manager.findProject(ownerModule);
    if (project == null) return false;

    Module depModule = orderEntry.getModule();
    if (depModule == null) return false;

    MavenProject depProject = manager.findProject(depModule);
    if (depProject == null) return false;

    for (MavenArtifact each : project.findDependencies(depProject)) {
      if (!shouldAddArtifact(each, productionOnly, runtimeOnly, compileOnly)) continue;

      boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(each.getType()) || "tests".equals(each.getClassifier());
      addOutput(depModule, isTestJar, urls);
    }
    return true;
  }

  private static void addOutput(Module module, boolean tests, Collection<String> urls) {
    CompilerModuleExtension ex = CompilerModuleExtension.getInstance(module);
    if (ex == null) return;

    String output = tests ? ex.getCompilerOutputUrlForTests() : ex.getCompilerOutputUrl();
    if (output != null) {
      urls.add(output);
    }
  }

  @Override
  public void addAdditionalRoots(@NotNull Module forModule,
                                 boolean productionOnly,
                                 boolean runtimeOnly,
                                 boolean compileOnly,
                                 @NotNull Collection<String> urls) {
    if (productionOnly) return;

    MavenProject project = MavenProjectsManager.getInstance(forModule.getProject()).findProject(forModule);
    if (project == null) return;

    Element config = project.getPluginConfiguration("org.apache.maven.plugins", "maven-surefire-plugin");
    for (String each : MavenJDOMUtil.findChildrenValuesByPath(config, "additionalClasspathElements", "additionalClasspathElement")) {
      urls.add(VfsUtil.pathToUrl(each));
    }
  }
}
