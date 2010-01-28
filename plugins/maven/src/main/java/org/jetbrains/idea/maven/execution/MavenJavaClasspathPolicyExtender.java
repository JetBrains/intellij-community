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
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.util.Collections;
import java.util.List;

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
    final boolean ignoredDependencyModules) {

    return new ProjectRootsTraversing.RootTraversePolicy.Visit<T>() {
      public void visit(T entry, ProjectRootsTraversing.TraverseState state, RootPolicy<ProjectRootsTraversing.TraverseState> policy) {
        Module ownerModule = entry.getOwnerModule();
        if (ignoredDependencyModules && originalModule != ownerModule && manager.findProject(ownerModule) != null) return;

        if (originalModule != ownerModule && entry instanceof ModuleSourceOrderEntry) {
          MavenProject project = manager.findProject(originalModule);
          MavenProject depProject = manager.findProject(ownerModule);

          if (project == null || depProject == null) {
            original.visit(entry, state, policy);
            return;
          }

          List<MavenArtifact> deps = project.findDependencies(depProject);
          if (hasDependency(deps, true) && original == ProjectClasspathTraversing.ALL_OUTPUTS) addOutput(ownerModule, true, state);
          if (hasDependency(deps, false)) addOutput(ownerModule, false, state);
        }
        else {
          original.visit(entry, state, policy);
        }
      }
    };
  }

  public void addOutput(Module module, boolean test, ProjectRootsTraversing.TraverseState state) {
    CompilerModuleExtension ex = CompilerModuleExtension.getInstance(module);
    if (ex == null) return;

    String output = test ? ex.getCompilerOutputUrlForTests() : ex.getCompilerOutputUrl();
    if (output != null) state.addAllUrls(Collections.singletonList(output));
  }

  private boolean hasDependency(List<MavenArtifact> deps, boolean test) {
    for (MavenArtifact each : deps) {
      if (test == MavenConstants.TYPE_TEST_JAR.equals(each.getType())) return true;
    }
    return false;
  }
}
