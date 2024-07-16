// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.move.moduleMembers;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMoveSymbolResult {
  private final List<PsiFile> myOptimizeImportsTargets;

  public PyMoveSymbolResult(@NotNull List<PsiFile> optimizeImportTargets) {
    myOptimizeImportsTargets = optimizeImportTargets;
  }

  public @NotNull List<PsiFile> getOptimizeImportsTargets() {
    return myOptimizeImportsTargets;
  }
}
