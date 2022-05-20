// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.service.project.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class MavenProjectImporterLegacyBase extends MavenProjectImporterBase {
  protected final Project myProject;
  protected final IdeModifiableModelsProvider myIdeModifiableModelsProvider;
  protected final ModifiableModelsProviderProxy myModelsProvider;
  protected final ModuleModelProxy myModuleModel;

  public MavenProjectImporterLegacyBase(Project project,
                                        MavenProjectsTree projectsTree,
                                        MavenImportingSettings importingSettings,
                                        Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                                        @NotNull IdeModifiableModelsProvider modelsProvider) {
    super(projectsTree, importingSettings, projectsToImportWithChanges);
    myProject = project;

    WorkspaceEntityStorageBuilder diff;

    if (modelsProvider instanceof IdeModifiableModelsProviderImpl) {
      IdeModifiableModelsProviderImpl impl = (IdeModifiableModelsProviderImpl)modelsProvider;
      diff = impl.getActualStorageBuilder();
    }
    else {
      diff = null;
    }

    myIdeModifiableModelsProvider = modelsProvider;

    if (MavenUtil.newModelEnabled(myProject) && diff != null) {
      myModelsProvider = new ModifiableModelsProviderProxyImpl(myProject, diff);
    }
    else {
      myModelsProvider = new ModifiableModelsProviderProxyWrapper(myIdeModifiableModelsProvider);
    }
    myModuleModel = myModelsProvider.getModuleModelProxy();
  }

  protected void configFacets(List<MavenModuleImporter> importers, List<MavenProjectsProcessorTask> postTasks) {
    if (!importers.isEmpty()) {
      IdeModifiableModelsProvider provider;
      if (myIdeModifiableModelsProvider instanceof IdeUIModifiableModelsProvider) {
        provider = myIdeModifiableModelsProvider; // commit does nothing for this provider, so it should be reused
      }
      else {
        provider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject);
      }

      try {
        List<MavenModuleImporter> toRun = ContainerUtil.filter(importers, it -> !it.isModuleDisposed() && !it.isAggregatorMainTestModule());

        toRun.forEach(importer -> importer.setModifiableModelsProvider(provider));
        toRun.forEach(importer -> importer.preConfigFacets());
        toRun.forEach(importer -> importer.configFacets(postTasks));
        toRun.forEach(importer -> importer.postConfigFacets());
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
          ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
            provider.commit();
          });
        });
      }
    }
  }

  protected void finalizeImport(List<Module> obsoleteModules) {
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> setMavenizedModules(obsoleteModules, false));
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> myModelsProvider.dispose());
  }

  protected void setMavenizedModules(final Collection<Module> modules, final boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    WorkspaceEntityStorage initialStorage = WorkspaceModel.getInstance(myProject).getEntityStorage().getCurrent();
    WorkspaceEntityStorageBuilder storageBuilder = WorkspaceEntityStorageBuilder.from(initialStorage);
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
    WorkspaceModel.getInstance(myProject).updateProjectModel(builder -> {
      builder.addDiff(storageBuilder);
      return null;
    });
  }
}
