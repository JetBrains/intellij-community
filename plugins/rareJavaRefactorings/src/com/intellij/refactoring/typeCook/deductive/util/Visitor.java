// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class Visitor extends JavaRecursiveElementWalkingVisitor {
  @Override public void visitPackage(final @NotNull PsiPackage aPackage) {
    final PsiDirectory[] dirs = aPackage.getDirectories();

    for (PsiDirectory dir : dirs) {
      final PsiFile[] files = dir.getFiles();

      for (final PsiFile file : files) {
        if (file instanceof PsiJavaFile) {
          super.visitJavaFile(((PsiJavaFile)file));
        }
      }
    }
  }
}
