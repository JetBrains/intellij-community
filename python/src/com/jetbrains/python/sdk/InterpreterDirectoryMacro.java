// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.PathMacro;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal

public final class InterpreterDirectoryMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "PyInterpreterDirectory";
  }

  @Override
  public @NotNull String getDescription() {
    return PyBundle.message("python.sdk.directory.macro.description");
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext) {
    Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        return null;
      }
      Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length == 0) {
        return null;
      }
      module = modules[0];
    }
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk != null) {
      VirtualFile homeDir = sdk.getHomeDirectory();
      if (homeDir == null) {
        return null;
      }
      String path = PathUtil.getLocalPath(homeDir.getParent());
      if (path != null) {
        return FileUtil.toSystemDependentName(path);
      }
    }
    return null;
  }
}