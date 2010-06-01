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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configurations.JavaClasspathPolicyExtender;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;

public class MavenJavaClasspathPolicyExtender implements JavaClasspathPolicyExtender {
  @NotNull
  public ProjectRootsTraversing.RootTraversePolicy extend(Project project, @NotNull ProjectRootsTraversing.RootTraversePolicy policy) {
    return policy;
  }

  @NotNull
  public ProjectRootsTraversing.RootTraversePolicy extend(final Module module, @NotNull ProjectRootsTraversing.RootTraversePolicy policy) {
    Project project = module.getProject();
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject() || !manager.isMavenizedModule(module)) return policy;

    return new ProjectRootsTraversing.RootTraversePolicy(extend(module, manager, policy.getVisitSource(), false),
                                                         policy.getVisitJdk(),
                                                         extend(module, manager, policy.getVisitLibrary(), true),
                                                         extend(module, manager, policy.getVisitModule(), true));
  }

  private <T extends OrderEntry> ProjectRootsTraversing.RootTraversePolicy.Visit<T> extend(
    final Module originalModule,
    final MavenProjectsManager manager,
    final ProjectRootsTraversing.RootTraversePolicy.Visit<T> original,
    final boolean skipDependencyModules) {

    return new ProjectRootsTraversing.RootTraversePolicy.Visit<T>() {
      public void visit(T entry, ProjectRootsTraversing.TraverseState state, RootPolicy<ProjectRootsTraversing.TraverseState> policy) {
        Module ownerModule = entry.getOwnerModule();
        if (skipDependencyModules && originalModule != ownerModule) return;

        if (originalModule != ownerModule && entry instanceof ModuleSourceOrderEntry) {
          MavenProject project = manager.findProject(originalModule);
          MavenProject depProject = manager.findProject(ownerModule);

          if (project == null || depProject == null) {
            original.visit(entry, state, policy);
            return;
          }

          for (MavenArtifact each : project.findDependencies(depProject)) {
            boolean isTestClasspath = original == ProjectClasspathTraversing.ALL_OUTPUTS;

            if (!isTestClasspath && MavenConstants.SCOPE_PROVIDEED.equals(each.getScope())) continue;
            if (isTestClasspath || !MavenConstants.SCOPE_TEST.equals(each.getScope())) {
              boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(each.getType()) || "tests".equals(each.getClassifier());
              addOutput(ownerModule, isTestJar, state);
            }
          }
        }
        else {
          // should be in some generic place
          if (entry instanceof ExportableOrderEntry) {
            boolean isTestClasspath = original == ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES
                                      || original == ProjectRootsTraversing.RootTraversePolicy.RECURSIVE;
            if (!isTestClasspath && ((ExportableOrderEntry)entry).getScope() == DependencyScope.PROVIDED) return;
          }
          original.visit(entry, state, policy);
        }
      }
    };
  }

  public void addOutput(Module module, boolean tests, ProjectRootsTraversing.TraverseState state) {
    CompilerModuleExtension ex = CompilerModuleExtension.getInstance(module);
    if (ex == null) return;

    String output = tests ? ex.getCompilerOutputUrlForTests() : ex.getCompilerOutputUrl();
    if (output != null) state.addAllUrls(Collections.singletonList(output));
  }
}
