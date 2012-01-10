package com.jetbrains.python.refactoring.surround;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.surround.surrounders.statements.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 7:09:36 PM
 */
public class PyStatementSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new PyWithIfSurrounder(),
    // new PyWithIfElseSurrounder(),
    new PyWithWhileSurrounder(),
    //new PyWithWhileElseSurrounder(),
    new PyWithReturnSurrounder(),
    new PyWithTryExceptSurrounder(),
    new PyWithTryFinallySurrounder()
  };

  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiElement[] statements = PyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) {
      return PsiElement.EMPTY_ARRAY;
    }
    return statements;
  }

  @NotNull
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
