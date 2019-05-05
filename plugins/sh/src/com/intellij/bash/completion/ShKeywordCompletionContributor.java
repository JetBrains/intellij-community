package com.intellij.bash.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.bash.completion.ShCompletionUtil.*;
import static com.intellij.patterns.PlatformPatterns.psiElement;

public class ShKeywordCompletionContributor extends CompletionContributor implements DumbAware {

  public ShKeywordCompletionContributor() {
    extend(CompletionType.BASIC, keywordElementPattern(), new ShKeywordCompletionProvider("if", "select", "case", "for", "while", "until", "function"));
    extend(CompletionType.BASIC, insideThenOrElse(), new ShKeywordCompletionProvider("elif"));
    extend(CompletionType.BASIC, insideCondition(), new ShKeywordCompletionProvider(true, "string equal",
        "string not equal", "string is empty", "string not empty", "number equal", "number not equal", "number less",
        "number less or equal", "number greater", "number greater or equal", "file exists", "file not empty",
        "command exists", "path exists", "directory exists", "file readable", "file writable", "file executable",
        "file equals", "file newer", "file older"));
  }

  private static PsiElementPattern.Capture<PsiElement> keywordElementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration(),
        insideCondition(), insideArithmeticExpansions(), insideOldArithmeticExpansions(), insideParameterExpansion()));
  }
}
