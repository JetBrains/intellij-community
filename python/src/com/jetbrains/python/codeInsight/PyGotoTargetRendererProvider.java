// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;


public final class PyGotoTargetRendererProvider implements GotoTargetRendererProvider {
  @Override
  public PsiElementListCellRenderer getRenderer(final @NotNull PsiElement element, @NotNull GotoTargetHandler.GotoData gotoData) {
    if (element instanceof PyElement && element instanceof PsiNamedElement) return new PyElementListCellRenderer();
    return null;
  }

}
