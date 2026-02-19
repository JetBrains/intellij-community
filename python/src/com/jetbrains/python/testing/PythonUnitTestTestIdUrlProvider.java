// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonUnitTestTestIdUrlProvider implements SMTestLocator, DumbAware {
  public static final String PROTOCOL_ID = "python_uttestid";

  public static final PythonUnitTestTestIdUrlProvider INSTANCE = new PythonUnitTestTestIdUrlProvider();

  @Override
  public @NotNull List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (!PROTOCOL_ID.equals(protocol)) {
      return Collections.emptyList();
    }

    final List<String> list = StringUtil.split(path, ".");
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final int listSize = list.size();

    // parse path as [ns.]*fileName.className[.methodName]

    if (listSize == 2) {
      return findLocations(project, list.get(0), list.get(1), null);
    }
    if (listSize > 2) {
      final String className = list.get(listSize - 2);
      final String methodName = list.get(listSize - 1);

      String fileName = list.get(listSize - 3);
      final List<Location> locations = findLocations(project, fileName, className, methodName);
      if (!locations.isEmpty()) {
        return locations;
      }
      return findLocations(project, list.get(listSize-2), list.get(listSize-1), null);
    }
    return Collections.emptyList();
  }

  public static List<Location> findLocations(final @NotNull Project project,
                                             @NotNull String fileName,
                                             @Nullable String className,
                                             @Nullable String methodName) {
    if (fileName.contains("%")) {
      fileName = fileName.substring(0, fileName.lastIndexOf("%"));
    }
    final List<Location> locations = new ArrayList<>();
    if (methodName == null && className == null) {
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (virtualFile == null) return locations;
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile != null) {
        locations.add(new PsiLocation<>(project, psiFile));
      }
    }

    if (className != null) {
      for (PyClass cls : PyClassNameIndex.find(className, project, false)) {
        ProgressManager.checkCanceled();

        final PsiFile containingFile = cls.getContainingFile();
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
        final String clsFileNameWithoutExt = FileUtilRt.getNameWithoutExtension(clsFileName);
        if (!clsFileNameWithoutExt.endsWith(fileName) && !fileName.equals(clsFileName)) {
          continue;
        }
        if (methodName == null) {
          locations.add(new PsiLocation<>(project, cls));
        }
        else {
          final PyFunction method = cls.findMethodByName(methodName, true, null);
          if (method == null) {
            continue;
          }

          locations.add(new PyPsiLocationWithFixedClass(project, method, cls));
        }
      }
    }
    else if (methodName != null) {
      for (PyFunction function : PyFunctionNameIndex.find(methodName, project)) {
        ProgressManager.checkCanceled();
        if (function.getContainingClass() == null) {
          final PsiFile containingFile = function.getContainingFile();
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
          final String clsFileNameWithoutExt = FileUtilRt.getNameWithoutExtension(clsFileName);
          if (!clsFileNameWithoutExt.endsWith(fileName)) {
            continue;
          }
          locations.add(new PsiLocation<>(project, function));
        }
      }
    }
    return locations;
  }
}
