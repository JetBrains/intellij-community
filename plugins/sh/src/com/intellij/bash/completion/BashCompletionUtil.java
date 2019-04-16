package com.intellij.bash.completion;

import com.intellij.bash.BashTypes;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.patterns.PlatformPatterns.psiElement;

class BashCompletionUtil {

  static PsiElementPattern.Capture<PsiElement> insideForClause() {
    return psiElement().inside(psiElement(BashTypes.FOR_CLAUSE));
  }

  static PsiElementPattern.Capture<PsiElement> insideIfDeclaration() {
    return psiElement().inside(psiElement(BashTypes.CONDITIONAL_COMMAND).inside(psiElement(BashTypes.IF_COMMAND)));
  }

  static PsiElementPattern.Capture<PsiElement> insideUntilDeclaration() {
    return psiElement().inside(psiElement(BashTypes.CONDITIONAL_COMMAND).inside(psiElement(BashTypes.UNTIL_COMMAND)));
  }

  static PsiElementPattern.Capture<PsiElement> insideWhileDeclaration() {
    return psiElement().inside(psiElement(BashTypes.CONDITIONAL_COMMAND).inside(psiElement(BashTypes.WHILE_COMMAND)));
  }

  static PsiElementPattern.Capture<PsiElement> insideFunctionDefinition() {
    return psiElement().inside(false, psiElement(BashTypes.FUNCTION_DEFINITION), psiElement(BashTypes.BLOCK));
  }

  static PsiElementPattern.Capture<PsiElement> insideSelectDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.SELECT_COMMAND), psiElement().andOr(psiElement(BashTypes.BLOCK), psiElement(BashTypes.DO_BLOCK)));
  }

  static PsiElementPattern.Capture<PsiElement> insideCaseDeclaration() {
    return psiElement().inside(false, psiElement(BashTypes.CASE_COMMAND), psiElement(BashTypes.CASE_CLAUSE));
  }
}
