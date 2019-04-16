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
    extend(CompletionType.BASIC, keywordElementPattern(), new BashKeywordCompletionProvider("if", "select", "case", "for", "while", "until", "function"));
    extend(CompletionType.BASIC, insideThenOrElse(), new BashKeywordCompletionProvider("elif"));
  }

  private static PsiElementPattern.Capture<PsiElement> keywordElementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration()));
  }
}
