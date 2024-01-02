// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.management.PythonPackageManager;
import com.jetbrains.python.packaging.management.PythonPackageManagerExt;
import com.jetbrains.python.sdk.PythonSdkUtil;

/**
 * User : catherine
 */
public final class RestPythonUtil {
  private RestPythonUtil() {}

  public static Presentation updateSphinxQuickStartRequiredAction(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      Module module = e.getData(PlatformCoreDataKeys.MODULE);
      if (module == null) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        module = modules.length == 0 ? null : modules [0];
      }
      if (module != null) {
        final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
        if (sdk != null) {
          PythonPackageManager manager = PythonPackageManager.Companion.forSdk(project, sdk);
          presentation.setEnabled(PythonPackageManagerExt.isInstalled(manager, "Sphinx"));
        }
      }
    }
    return presentation;
  }
}