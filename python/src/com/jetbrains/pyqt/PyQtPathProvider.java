// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.pyqt;

import com.intellij.lang.qt.QtPathProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public class PyQtPathProvider implements QtPathProvider {
  @Override
  public @Nullable Path findQtTool(@NotNull Project project, @NotNull VirtualFile file, @NotNull String toolName) {
    return findQtToolImpl(ModuleUtilCore.findModuleForFile(file, project), toolName);
  }

  @Override
  public @Nullable Path findQtTool(@NotNull Project project, @NotNull String toolName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] allModules = moduleManager.getModules();
    for (Module module : allModules) {
      if (!module.isLoaded() || PythonSdkUtil.findPythonSdk(module) == null) continue;
      @Nullable Path toolPath = findQtToolImpl(module, DEFAULT_TOOL_NAME);
      if (toolPath != null) return toolPath;
    }
    return null;
  }

  public static @Nullable Path findQtToolImpl(@Nullable Module module, String toolName) {
    if (SystemInfo.isWindows) {
      if (module == null) {
        return null;
      }
      Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk == null) {
        return null;
      }

      return ReadAction.compute(() -> {
        // In Python 3.10 the package name is PySide2 for Qt5 and PySide6 for Qt6
        Path tool = findToolInPackage(toolName, module, "PySide6");
        if (tool != null) {
          return tool;
        }
        tool = findToolInPackage(toolName, module, "PySide2");
        if (tool != null) {
          return tool;
        }

        tool = findToolInPackage(toolName, module, "PyQt4");
        if (tool != null) {
          return tool;
        }
        return findToolInPackage(toolName, module, "PySide");
      });
    }
    // TODO
    return null;
  }

  @Nullable
  private static Path findToolInPackage(String toolName, Module module, String name) {
    final List<PsiElement> results = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString(name),
                                                                              PyResolveImportUtil.fromModule(module));
    return StreamEx.of(results).select(PsiDirectory.class)
      .map(directory -> directory.getVirtualFile().findChild(toolName + ".exe"))
      .nonNull()
      .map(VirtualFile::toNioPath)
      .findFirst()
      .orElse(null);
  }
}
