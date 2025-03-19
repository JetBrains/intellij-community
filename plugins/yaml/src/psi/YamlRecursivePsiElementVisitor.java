// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;

public abstract class YamlRecursivePsiElementVisitor extends YamlPsiElementVisitor implements PsiRecursiveVisitor {
  @Override
  public void visitElement(@NotNull PsiElement element) {
    ProgressManager.checkCanceled();
    element.acceptChildren(this);
  }
}
