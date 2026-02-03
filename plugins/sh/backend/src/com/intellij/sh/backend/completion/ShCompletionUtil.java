// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShTokenTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

final class ShCompletionUtil {
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

  static PsiElementPattern.Capture<PsiElement> insideCommandSubstitution() {
    return psiElement().inside(psiElement(ShTypes.COMMAND_SUBSTITUTION_COMMAND));
  }

  static PsiElementPattern.Capture<PsiElement> insideSubshellCommand() {
    return psiElement().inside(psiElement(ShTypes.SUBSHELL_COMMAND));
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

  static PsiElementPattern.Capture<PsiElement> insideString() {
    return psiElement().inside(psiElement(ShTypes.STRING_CONTENT));
  }

  static PsiElementPattern.Capture<PsiElement> insideRawString() {
    return psiElement().inside(psiElement(ShTypes.RAW_STRING));
  }

  static PsiElementPattern.Capture<PsiElement> insideComment() {
    return psiElement().inside(psiElement(ShTokenTypes.COMMENT));
  }

  static boolean endsWithDot(@NotNull CompletionParameters parameters) {
    PsiElement original = parameters.getOriginalPosition();
    return original != null && original.getText().endsWith(".");
  }

  private static PsiElementPattern.Capture<PsiElement> blockExpression() {
    return psiElement().andOr(psiElement(ShTypes.BLOCK), psiElement(ShTypes.DO_BLOCK));
  }
}
