package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TextMateLanguage;

import java.util.Collections;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

public class TextMateCompletionContributor extends CompletionContributor implements DumbAware {
  public TextMateCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(psiFile().withLanguage(TextMateLanguage.LANGUAGE)),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               String prefix = CompletionUtil.findJavaIdentifierPrefix(parameters);
               CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);

               WordCompletionContributor.addWordCompletionVariants(resultSetWithPrefix, parameters, Collections.emptySet(), true);
             }
           });
  }
}
