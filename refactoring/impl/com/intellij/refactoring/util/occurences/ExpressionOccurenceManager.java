package com.intellij.refactoring.util.occurences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author dsl
 */
public class ExpressionOccurenceManager extends BaseOccurenceManager {
  private PsiExpression myMainOccurence;
  private final PsiElement myScope;
  private final boolean myMaintainStaticContext;

  public ExpressionOccurenceManager(PsiExpression mainOccurence, PsiElement scope, OccurenceFilter filter) {
    this(mainOccurence, scope, filter, false);
  }

  public ExpressionOccurenceManager(PsiExpression mainOccurence, PsiElement scope, OccurenceFilter filter, boolean maintainStaticContext) {
    super(filter);
    myMainOccurence = mainOccurence;
    myScope = scope;
    myMaintainStaticContext = maintainStaticContext;
  }
  protected PsiExpression[] defaultOccurences() {
    return new PsiExpression[]{myMainOccurence};
  }

  public PsiExpression getMainOccurence() {
    return myMainOccurence;
  }

  protected PsiExpression[] findOccurences() {
    if("null".equals(myMainOccurence.getText())) {
      return defaultOccurences();
    }
    if(myFilter != null && !myFilter.isOK(myMainOccurence)) {
      return defaultOccurences();
    }
    final PsiExpression[] expressionOccurrences = findExpressionOccurrences();
    final PsiClass scopeClass = PsiTreeUtil.getNonStrictParentOfType(myScope, PsiClass.class);
    if (myMaintainStaticContext && expressionOccurrences.length > 1 && !RefactoringUtil.isInStaticContext(myMainOccurence, scopeClass)) {
      final ArrayList<PsiExpression> expressions = new ArrayList<PsiExpression>(Arrays.asList(expressionOccurrences));
      for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext();) {
        final PsiExpression expression = iterator.next();
        if(RefactoringUtil.isInStaticContext(expression, scopeClass)) {
          iterator.remove();
        }
      }
      return expressions.toArray(new PsiExpression[expressions.size()]);
    }
    else {
      return expressionOccurrences;
    }
  }

  public PsiElement getScope() {
    return myScope;
  }

  protected PsiExpression[] findExpressionOccurrences() {
    return CodeInsightUtil.findExpressionOccurrences(myScope, myMainOccurence);
  }

}
