// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performanceScripts.lang.psi.IJPerfCommandName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class IJPerfCompletionContributor extends CompletionContributor implements DumbAware {

  public IJPerfCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(IJPerfCommandName.class)),
           new IJPerfCompletionProvider());
  }

  private static class IJPerfCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      List<LookupElement> commands = ContainerUtil.append(ContainerUtil.map(
        CommandProvider.getAllCommandNames(),
        LookupElementBuilder::create
      ),
      LookupElementBuilder.create("%%project"),
      LookupElementBuilder.create("%%profileIndexing"));
      String prefix = CompletionUtil.findIdentifierPrefix(parameters.getPosition(), parameters.getOffset(),
                                                          StandardPatterns.character(), StandardPatterns.character());
      result.withPrefixMatcher(prefix).addAllElements(commands);
    }
  }
}
