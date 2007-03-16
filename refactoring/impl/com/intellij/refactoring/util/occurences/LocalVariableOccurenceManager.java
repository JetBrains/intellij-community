package com.intellij.refactoring.util.occurences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.util.RefactoringUtil;

/**
 * @author dsl
 */
public class LocalVariableOccurenceManager extends BaseOccurenceManager {
  private final PsiLocalVariable myLocalVariable;

  public LocalVariableOccurenceManager(PsiLocalVariable localVariable, OccurenceFilter filter) {
    super(filter);
    myLocalVariable = localVariable;
  }

  public PsiExpression[] defaultOccurences() {
    return PsiExpression.EMPTY_ARRAY;
  }

  public PsiExpression[] findOccurences() {
    return CodeInsightUtil.findReferenceExpressions(RefactoringUtil.getVariableScope(myLocalVariable), myLocalVariable);
  }


}

