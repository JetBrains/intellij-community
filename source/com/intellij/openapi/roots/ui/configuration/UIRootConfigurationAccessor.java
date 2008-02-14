package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UIRootConfigurationAccessor extends RootConfigurationAccessor {
  private Project myProject;

  public UIRootConfigurationAccessor(final Project project) {
    myProject = project;
  }

  @Nullable
  public Library getLibrary(Library library, final String libraryName, final String libraryLevel) {
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(myProject).getContext();
    if (library == null) {
      if (libraryName != null) {
        library = context.getLibrary(libraryName, libraryLevel);
      }
    } else {
      library = context.getLibrary(library.getName(), library.getTable().getTableLevel());
    }
    return library;
  }

  @Nullable
  public Sdk getSdk(final Sdk sdk, final String sdkName) {
    final ProjectJdksModel model = ProjectStructureConfigurable.getInstance(myProject).getJdkConfig().getJdksTreeModel();
    return sdkName != null ? model.findSdk(sdkName) : sdk;
  }

  public Module getModule(final Module module, final String moduleName) {
    if (module == null) {
      return ModuleStructureConfigurable.getInstance(myProject).getModule(moduleName);
    }
    return module;
  }

  public Sdk getProjectSdk(final Project project) {
    return ProjectJdksModel.getInstance(project).getProjectJdk();
  }

  public String getProjectSdkName(final Project project) {
    final String projectJdkName = ProjectRootManager.getInstance(project).getProjectJdkName();
    final Sdk projectJdk = getProjectSdk(project);
    if (projectJdk != null) {
      return projectJdk.getName();
    }
    else {
      return ProjectJdksModel.getInstance(project).findSdk(projectJdkName) == null ? projectJdkName : null;
    }
  }
}
