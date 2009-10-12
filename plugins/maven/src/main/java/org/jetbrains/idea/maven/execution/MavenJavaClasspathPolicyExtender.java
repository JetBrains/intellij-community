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
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.roots.RootPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenJavaClasspathPolicyExtender implements JavaClasspathPolicyExtender  {
  @NotNull
  public ProjectRootsTraversing.RootTraversePolicy extend(Project project,
                                                          @NotNull ProjectRootsTraversing.RootTraversePolicy policy) {
    return policy;
  }

  @NotNull
  public ProjectRootsTraversing.RootTraversePolicy extend(final Module module,
                                                          @NotNull ProjectRootsTraversing.RootTraversePolicy policy) {
    Project project = module.getProject();
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject() || !manager.isMavenizedModule(module)) return policy;

    return new ProjectRootsTraversing.RootTraversePolicy(policy.getVisitSource(),
                                                         policy.getVisitJdk(),
                                                         extend(module, manager, policy.getVisitLibrary()),
                                                         extend(module, manager, policy.getVisitModule()));
  }

  private <T extends OrderEntry> ProjectRootsTraversing.RootTraversePolicy.Visit<T> extend(
    final Module originalModule,
    final MavenProjectsManager manager,
    final ProjectRootsTraversing.RootTraversePolicy.Visit<T> original) {
    return new ProjectRootsTraversing.RootTraversePolicy.Visit<T>() {
      public void visit(T entry, ProjectRootsTraversing.TraverseState state, RootPolicy<ProjectRootsTraversing.TraverseState> policy) {
        Module ownerModule = entry.getOwnerModule();
        if (originalModule != ownerModule && manager.findProject(ownerModule) != null) return;

        original.visit(entry, state, policy);
      }
    };
  }
}
