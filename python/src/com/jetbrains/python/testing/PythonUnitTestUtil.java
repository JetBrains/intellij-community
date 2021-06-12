// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PythonUnitTestUtil {
  private PythonUnitTestUtil() {}

  public static List<Location> findLocations(@NotNull final Project project,
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
