package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configurations.JavaClasspathPolicyExtender;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
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
        if (shouldSkip(originalModule, entry, manager)) return;
        original.visit(entry, state, policy);
      }
    };
  }

  private boolean shouldSkip(Module originalModule, OrderEntry orderEntry, MavenProjectsManager manager) {
    Module ownerModule = orderEntry.getOwnerModule();
    if (originalModule == ownerModule) return false;

    MavenProject ownerProject = manager.findProject(ownerModule);
    if (ownerProject == null) return false;

    MavenId depId;
    if (orderEntry instanceof ModuleOrderEntry) {
      Module module = ((ModuleOrderEntry)orderEntry).getModule();
      if (module == null) return false;

      MavenProject depProject = manager.findProject(module);
      if (depProject == null) return false;

      depId = depProject.getMavenId();
    } else if (orderEntry instanceof LibraryOrderEntry) {
      Library lib = ((LibraryOrderEntry)orderEntry).getLibrary();
      depId = MavenRootModelAdapter.getMavenId(ownerProject, lib);
      if (depId == null) return false;
    } else {
      return false;
    }

    return ownerProject.isOptionalDependency(depId);
  }
}
