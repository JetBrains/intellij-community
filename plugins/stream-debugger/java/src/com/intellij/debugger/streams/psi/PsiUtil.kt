// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public final class PsiUtil {
  private PsiUtil() {}

  public static @NotNull PsiElement ignoreWhiteSpaces(@NotNull PsiElement element) {
    PsiElement result = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
    if (result == null) {
      result = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      if (result == null) {
        result = element;
      }
    }

    return result;
  }
}
