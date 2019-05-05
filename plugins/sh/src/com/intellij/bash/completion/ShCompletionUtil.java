package com.intellij.bash.completion;

import com.intellij.bash.ShTypes;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.patterns.PlatformPatterns.psiElement;

class ShCompletionUtil {
  static PsiElementPattern.Capture<PsiElement> insideForClause() {
    return psiElement().inside(false, psiElement(ShTypes.FOR_COMMAND), blockExpression());
  }

  static PsiElementPattern.Capture<PsiElement> insideIfDeclaration() {
    return psiElement().inside(false, psiElement(ShTypes.IF_COMMAND), psiElement().andOr(psiElement(ShTypes.THEN_CLAUSE),
        psiElement(ShTypes.ELSE_CLAUSE)));
  }

  static PsiElementPattern.Capture<PsiElement> insideThenOrElse() {
    return psiElement().inside(psiElement().andOr(psiElement(ShTypes.THEN_CLAUSE), psiElement(ShTypes.ELSE_CLAUSE)));
  }

  static PsiElementPattern.Capture<PsiElement> insideCondition() {
    return psiElement().inside(psiElement(ShTypes.CONDITIONAL_COMMAND));
  }

  static PsiElementPattern.Capture<PsiElement> insideArithmeticExpansions() {
    return psiElement().inside(psiElement(ShTypes.ARITHMETIC_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideOldArithmeticExpansions() {
    return psiElement().inside(psiElement(ShTypes.OLD_ARITHMETIC_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideParameterExpansion() {
    return psiElement().inside(psiElement(ShTypes.SHELL_PARAMETER_EXPANSION));
  }

  static PsiElementPattern.Capture<PsiElement> insideUntilDeclaration() {
    return psiElement().inside(false, psiElement(ShTypes.UNTIL_COMMAND), psiElement(ShTypes.DO_BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideWhileDeclaration() {
    return psiElement().inside(false, psiElement(ShTypes.WHILE_COMMAND), psiElement(ShTypes.DO_BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideFunctionDefinition() {
    return psiElement().inside(false, psiElement(ShTypes.FUNCTION_DEFINITION), psiElement(ShTypes.BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideSelectDeclaration() {
    return psiElement().inside(false, psiElement(ShTypes.SELECT_COMMAND), blockExpression());
  }

  static PsiElementPattern.Capture<PsiElement> insideCaseDeclaration() {
    return psiElement().inside(false, psiElement(ShTypes.CASE_COMMAND), psiElement(ShTypes.CASE_CLAUSE));
  }

  private static PsiElementPattern.Capture<PsiElement> blockExpression() {
    return psiElement().andOr(psiElement(ShTypes.BLOCK), psiElement(ShTypes.DO_BLOCK));
  }
}
