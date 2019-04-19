package com.intellij.bash.completion;

import com.intellij.bash.BashTypes;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.patterns.PlatformPatterns.psiElement;

class BashCompletionUtil {

  static PsiElementPattern.Capture<PsiElement> insideForClause() {
    return psiElement().inside(false, psiElement(BashTypes.FOR_COMMAND), blockExpression());
  }

  static PsiElementPattern.Capture<PsiElement> insideIfDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.IF_COMMAND), psiElement().andOr(psiElement(BashTypes.THEN_CLAUSE),
        psiElement(BashTypes.ELSE_CLAUSE)));
  }

  static PsiElementPattern.Capture<PsiElement> insideThenOrElse() {
    return psiElement().inside(psiElement().andOr(psiElement(BashTypes.THEN_CLAUSE), psiElement(BashTypes.ELSE_CLAUSE)));
  }

  static PsiElementPattern.Capture<PsiElement> insideCondition() {
    return psiElement().inside(psiElement(BashTypes.CONDITIONAL_COMMAND));
  }

  static PsiElementPattern.Capture<PsiElement> insideArithmeticExpansions() {
    return psiElement().inside(psiElement(BashTypes.ARITHMETIC_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideOldArithmeticExpansions() {
    return psiElement().inside(psiElement(BashTypes.OLD_ARITHMETIC_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideParameterExpansion() {
    return psiElement().inside(psiElement(BashTypes.SHELL_PARAMETER_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideUntilDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.UNTIL_COMMAND), psiElement(BashTypes.DO_BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideWhileDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.WHILE_COMMAND), psiElement(BashTypes.DO_BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideFunctionDefinition() {
    return psiElement().inside(false, psiElement(BashTypes.FUNCTION_DEFINITION), psiElement(BashTypes.BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideSelectDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.SELECT_COMMAND), blockExpression());
  }

  static PsiElementPattern.Capture<PsiElement> insideCaseDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.CASE_COMMAND), psiElement(BashTypes.CASE_CLAUSE));
  }

  private static PsiElementPattern.Capture<PsiElement> blockExpression() {
    return psiElement().andOr(psiElement(BashTypes.BLOCK), psiElement(BashTypes.DO_BLOCK));
  }
}
