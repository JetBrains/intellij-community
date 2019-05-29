// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.sh.completion.ShCompletionUtil.*;

public class ShKeywordCompletionContributor extends CompletionContributor implements DumbAware {
  private static final String BASE_KEYWORD_COMPLETION_ACTION = "BaseKeywordCompletionUsed";
  private static final String CONDITION_KEYWORD_COMPLETION_ACTION = "ConditionKeywordCompletionUsed";

  public ShKeywordCompletionContributor() {
    extend(CompletionType.BASIC, keywordElementPattern(), new ShKeywordCompletionProvider(BASE_KEYWORD_COMPLETION_ACTION,
                                                  "if", "select", "case", "for", "while", "until", "function"));
    extend(CompletionType.BASIC, elifElementPattern(), new ShKeywordCompletionProvider(BASE_KEYWORD_COMPLETION_ACTION, "elif"));
    extend(CompletionType.BASIC, insideCondition(), new ShKeywordCompletionProvider(CONDITION_KEYWORD_COMPLETION_ACTION, true,
        "string equal", "string not equal", "string is empty", "string not empty", "number equal", "number not equal", "number less",
        "number less or equal", "number greater", "number greater or equal", "file exists", "file not empty", "command exists",
        "path exists", "directory exists", "file readable", "file writable", "file executable", "file equals", "file newer", "file older"));
  }

  @NotNull
  private static PsiElementPattern.Capture<PsiElement> keywordElementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration(),
        insideCondition(), insideArithmeticExpansions(), insideOldArithmeticExpansions(), insideParameterExpansion(),
        insideRawString(), insideString(), insideComment()));
  }

  @NotNull
  private static PsiElementPattern.Capture<PsiElement> elifElementPattern() {
    return insideThenOrElse().andNot(psiElement().andOr(insideRawString(), insideString(), insideComment()));
  }
}
