// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;

public class MavenImportUtil {
  private MavenImportUtil() { }

  public static Module createDummyModule(Project project, VirtualFile contentRoot) {
    return WriteAction.compute(() -> {
      Module module = ModuleManager.getInstance(project)
        .newModule(contentRoot.toNioPath(), ModuleTypeManager.getInstance().getDefaultModuleType().getId());
      ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
      modifiableModel.addContentEntry(contentRoot);
      modifiableModel.commit();
      //if (MavenProjectImporter.isImportToWorkspaceModelEnabled() || MavenProjectImporter.isImportToTreeStructureEnabled(project)) {
        //this is needed to ensure that dummy module created here will be correctly replaced by real ModuleEntity when import finishes
        ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true);
  //    }
      return module;
    });
  }

  private static void renameModuleToProjectName(Project project, Module module) {
    try {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
      moduleModel.renameModule(module, module.getName());
      moduleModel.commit();
    }
    catch (ModuleWithNameAlreadyExists ignore) {
    }
  }
}
