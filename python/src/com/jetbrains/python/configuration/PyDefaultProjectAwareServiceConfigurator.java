// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.packaging.PyPackageRequirementsSettings;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Configures {@link PyDefaultProjectAwareService}s
 * for new project.
 */
final class PyDefaultProjectAwareServiceConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef, boolean isProjectCreatedWithWizard) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }

    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      if (ModuleType.get(module) instanceof PythonModuleTypeBase) {
        updateServices(module, isProjectCreatedWithWizard);
        break;
      }
    }
  }

  private static void updateServices(@NotNull Module module, boolean newProject) {
    List<PyDefaultProjectAwareServiceModuleConfigurator> configurators = Arrays.asList(
      TestRunnerService.getConfigurator(),
      PyDocumentationSettings.getConfigurator(),
      ReSTService.getConfigurator(),
      PyPackageRequirementsSettings.getConfigurator());
    for (PyDefaultProjectAwareServiceModuleConfigurator configurator : configurators) {
      configurator.configureModule(module, newProject);
    }
  }
}
