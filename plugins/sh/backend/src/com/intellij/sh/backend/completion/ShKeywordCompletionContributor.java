// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.sh.backend.completion.ShCompletionUtil.*;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.BASE_KEYWORD_COMPLETION_USED_EVENT_ID;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.CONDITION_KEYWORD_COMPLETION_USED_EVENT_ID;

public class ShKeywordCompletionContributor extends CompletionContributor implements DumbAware {
  public ShKeywordCompletionContributor() {
    extend(CompletionType.BASIC, keywordElementPattern(), new ShKeywordCompletionProvider(BASE_KEYWORD_COMPLETION_USED_EVENT_ID,
                                                  "if", "select", "case", "for", "while", "until", "function"));
    extend(CompletionType.BASIC, elifElementPattern(), new ShKeywordCompletionProvider(BASE_KEYWORD_COMPLETION_USED_EVENT_ID, "elif"));
    extend(CompletionType.BASIC, insideCondition(), new ShKeywordCompletionProvider(CONDITION_KEYWORD_COMPLETION_USED_EVENT_ID, true,
        "string equal", "string not equal", "string is empty", "string not empty", "number equal", "number not equal", "number less",
        "number less or equal", "number greater", "number greater or equal", "file exists", "file not empty", "command exists",
        "path exists", "directory exists", "file readable", "file writable", "file executable", "file equals", "file newer", "file older"));
  }

  private static @NotNull PsiElementPattern.Capture<PsiElement> keywordElementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration(),
        insideCondition(), insideArithmeticExpansions(), insideOldArithmeticExpansions(), insideParameterExpansion(),
        insideCommandSubstitution(), insideSubshellCommand(), insideRawString(), insideString(), insideComment()));
  }

  private static @NotNull PsiElementPattern.Capture<PsiElement> elifElementPattern() {
    return insideThenOrElse().andNot(psiElement().andOr(insideRawString(), insideString(), insideComment()));
  }
}
