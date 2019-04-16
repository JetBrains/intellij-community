package com.intellij.bash.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.bash.completion.BashCompletionUtil.*;
import static com.intellij.patterns.PlatformPatterns.psiElement;

public class BashKeywordCompletionContributor extends CompletionContributor implements DumbAware {

  public BashKeywordCompletionContributor() {
    extend(CompletionType.BASIC, elementPattern(), new BashKeywordCompletionProvider("if", "elif", "select", "case", "for", "while", "until", "function"));
  }

  private static PsiElementPattern.Capture<PsiElement> elementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration()));
  }
}
