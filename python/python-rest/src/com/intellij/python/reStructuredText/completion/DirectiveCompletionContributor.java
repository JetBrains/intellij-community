// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.python.reStructuredText.RestTokenTypes;
import com.intellij.python.reStructuredText.RestUtil;
import com.intellij.python.reStructuredText.psi.RestReferenceTarget;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.python.reStructuredText.completion.ReferenceCompletionContributor.getPrefix;

/**
 * User : catherine
 */
public class DirectiveCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PsiElement> DIRECTIVE_PATTERN = psiElement().afterSibling(or(psiElement().
    withElementType(RestTokenTypes.WHITESPACE).afterSibling(psiElement(RestReferenceTarget.class)),
       psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START)));

  public DirectiveCompletionContributor() {
    extend(CompletionType.BASIC, DIRECTIVE_PATTERN,
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               int offset = parameters.getOffset();
               final String prefix = getPrefix(offset, parameters.getOriginalFile());
               if (prefix.length() > 0) {
                 result = result.withPrefixMatcher(prefix);
               }
               for (String tag : RestUtil.getDirectives()) {
                 result.addElement(LookupElementBuilder.create(tag));
               }
             }
           });
  }
}
