// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.surround.surrounders.statements.*;
import org.jetbrains.annotations.NotNull;

public final class PyStatementSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new PyWithIfSurrounder(),
    // new PyWithIfElseSurrounder(),
    new PyWithWhileSurrounder(),
    //new PyWithWhileElseSurrounder(),
    new PyWithReturnSurrounder(),
    new PyWithTryExceptSurrounder(),
    new PyWithTryFinallySurrounder()
  };

  @Override
  public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiElement[] statements = PyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) {
      return PsiElement.EMPTY_ARRAY;
    }
    return statements;
  }

  @Override
  public Surrounder @NotNull [] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
