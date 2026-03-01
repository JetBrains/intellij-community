// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;

/**
 * The default behavior of enabling "unresolved reference" inspection that can be overridden.
 * Must be registered last.
 * Keeping it here instead of the inspection class itself is necessary for decoupling intellij.python.psi.impl.
 */
public final class PyUnresolvedReferenceDefaultInspectionExtension extends PyInspectionExtension {
  @Override
  public Boolean overrideUnresolvedReferenceInspection(@NotNull PsiFile file) {
    boolean result;
    if (PySkeletonRefresher.isGeneratingSkeletons()) {
      result = false;
    }
    else {
      var pythonRuntimeService = PythonRuntimeService.getInstance();
      result = PythonSdkUtil.findPythonSdk(file) != null || pythonRuntimeService.isInScratchFile(file) || pythonRuntimeService.isExternallyIndexedFile(file);
    }
    return result;
  }
}
