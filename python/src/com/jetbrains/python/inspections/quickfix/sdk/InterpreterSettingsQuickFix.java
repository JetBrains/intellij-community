// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyPsiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class InterpreterSettingsQuickFix implements LocalQuickFix {

    @NotNull
    private final Module myModule;

    public InterpreterSettingsQuickFix(@NotNull Module module) {
    myModule = module;
  }

    @NotNull
    @Override
    public String getFamilyName() {
    return PlatformUtils.isPyCharm()
           ? PyPsiBundle.message("INSP.interpreter.interpreter.settings")
           : PyPsiBundle.message("INSP.interpreter.configure.python.interpreter");
  }

    @Override
    public boolean startInWriteAction() {
    return false;
  }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    showPythonInterpreterSettings(project, myModule);
  }

    public static void showPythonInterpreterSettings(@NotNull Project project, @Nullable Module module) {
    final var id = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable";
    final var group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true);
    if (ConfigurableVisitor.findById(id, Collections.singletonList(group)) != null) {
      ShowSettingsUtilImpl.showSettingsDialog(project, id, null);
      return;
    }

    final ProjectSettingsService settingsService = ProjectSettingsService.getInstance(project);
    if (module == null || justOneModuleInheritingSdk(project, module)) {
      settingsService.openProjectSettings();
    }
    else {
      settingsService.openModuleSettings(module);
    }
  }

    private static boolean justOneModuleInheritingSdk(@NotNull Project project, @NotNull Module module) {
    return ProjectRootManager.getInstance(project).getProjectSdk() == null &&
           ModuleRootManager.getInstance(module).isSdkInherited() &&
           ModuleManager.getInstance(project).getModules().length < 2;
  }
}
