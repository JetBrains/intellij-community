// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModulePropertyManagerBridge;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.MutableEntityStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class MavenProjectImporterLegacyBase extends MavenProjectImporterBase {
  protected final ModuleModelProxy myModuleModel;
  protected volatile Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;

  public MavenProjectImporterLegacyBase(Project project,
                                        MavenProjectsTree projectsTree,
                                        MavenImportingSettings importingSettings,
                                        Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                                        @NotNull IdeModifiableModelsProvider modelsProvider) {
    super(project, projectsTree, importingSettings, modelsProvider);
    myProjectsToImportWithChanges = projectsToImportWithChanges;
    myModuleModel = myModelsProvider.getModuleModelProxy();
  }

  protected void finalizeImport(List<Module> obsoleteModules) {
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> setMavenizedModules(obsoleteModules, false));
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> myModelsProvider.dispose());
  }

  protected void setMavenizedModules(final Collection<Module> modules, final boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    EntityStorage initialStorage = WorkspaceModel.getInstance(myProject).getEntityStorage().getCurrent();
    MutableEntityStorage storageBuilder = MutableEntityStorage.from(initialStorage);
    for (Module module : modules) {
      if (module.isDisposed()) continue;
      ExternalSystemModulePropertyManager modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module);
      if (modulePropertyManager instanceof ExternalSystemModulePropertyManagerBridge &&
          module instanceof ModuleBridge &&
          ((ModuleBridge)module).getDiff() == null) {
        ((ExternalSystemModulePropertyManagerBridge)modulePropertyManager).setMavenized(mavenized, storageBuilder);
      }
      else {
        modulePropertyManager.setMavenized(mavenized);
      }
    }
    WorkspaceModel.getInstance(myProject).updateProjectModel("Set mavenized modules", builder -> {
      builder.addDiff(storageBuilder);
      return null;
    });
  }
}
