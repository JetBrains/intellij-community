// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.extensions.CaptureExtKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


@ApiStatus.Internal
public final class PyParameterCompletionContributor extends CompletionContributor implements DumbAware {
  public PyParameterCompletionContributor() {
    extend(CompletionType.BASIC,
           CaptureExtKt.inParameterList(psiElement()).afterLeaf("*"),
           new ParameterCompletionProvider("args"));
    extend(CompletionType.BASIC,
           CaptureExtKt.inParameterList(psiElement()).afterLeaf("**"),
           new ParameterCompletionProvider("kwargs"));
  }

  private static final class ParameterCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final String myName;

    private ParameterCompletionProvider(String name) {
      myName = name;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create(myName).withIcon(AllIcons.Nodes.Parameter));
    }
  }
}
