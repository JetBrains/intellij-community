// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.restructuredtext.RestTokenTypes;
import com.intellij.util.ProcessingContext;
import com.intellij.restructuredtext.RestUtil;
import com.intellij.restructuredtext.psi.RestReferenceTarget;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * User : ktisha
 */
public final class SphinxDirectiveCompletionContributor extends CompletionContributor implements DumbAware {
  public static final PsiElementPattern.Capture<PsiElement> DIRECTIVE_PATTERN = psiElement().afterSibling(or(psiElement().
    withElementType(RestTokenTypes.WHITESPACE).afterSibling(psiElement(RestReferenceTarget.class)),
       psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START)));

  public SphinxDirectiveCompletionContributor() {
    extend(CompletionType.BASIC, DIRECTIVE_PATTERN,
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               Sdk sdk = ProjectRootManager.getInstance(parameters.getPosition().getProject()).getProjectSdk();
               if (sdk != null) {
                 for (String tag : RestUtil.SPHINX_DIRECTIVES) {
                   result.addElement(LookupElementBuilder.create(tag));
                 }
               }
             }
           }
       );
  }
}
