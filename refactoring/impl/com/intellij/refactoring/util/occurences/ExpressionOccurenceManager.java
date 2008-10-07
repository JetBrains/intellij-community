package com.intellij.refactoring.util.occurences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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

  public PsiExpression[] findExpressionOccurrences() {
    if (myMainOccurence instanceof PsiLiteralExpression && !myMainOccurence.isPhysical()) {
      final FindManager findManager = FindManager.getInstance(getScope().getProject());
      final FindModel findModel = (FindModel)findManager.getFindInFileModel().clone();
      findModel.setStringToFind(StringUtil.stripQuotesAroundValue(myMainOccurence.getText()));
      final List<PsiExpression> results = new ArrayList<PsiExpression>();
      final PsiFile file = getScope().getContainingFile();
      final String text = getScope().getText();
      final int offset = getScope().getTextRange().getStartOffset();
      FindResult result = findManager.findString(text, 0, findModel);
      while (result.isStringFound()) {
        final int startOffset = offset + result.getStartOffset();
        final int endOffset = result.getEndOffset();
        if (PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), PsiLiteralExpression.class) !=
            null) { //enum. occurrences inside string literals
          results.add(IntroduceVariableBase.getSelectedExpression(file.getProject(), file, startOffset, offset + endOffset));
        }
        result = findManager.findString(text, endOffset, findModel);
      }
      return results.toArray(new PsiExpression[results.size()]);
    }
    return CodeInsightUtil.findExpressionOccurrences(myScope, myMainOccurence);
  }
}
