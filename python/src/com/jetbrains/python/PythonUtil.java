// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class PythonUtil {
  public static boolean isInScratchFile(@NotNull PsiElement element) {
    return ScratchFileService.isInScratchRoot(PsiUtilCore.getVirtualFile(element));
  }
}
