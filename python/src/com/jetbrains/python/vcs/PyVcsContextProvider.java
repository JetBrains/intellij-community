// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.vcs;

import com.intellij.codeInsight.hints.VcsCodeVisionLanguageContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

public final class PyVcsContextProvider implements VcsCodeVisionLanguageContext  {
  @Override
  public boolean isAccepted(@NotNull PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction;
  }

  @Override
  public void handleClick(@NotNull MouseEvent mouseEvent,
                          @NotNull Editor editor,
                          @NotNull PsiElement element) {

  }
}
