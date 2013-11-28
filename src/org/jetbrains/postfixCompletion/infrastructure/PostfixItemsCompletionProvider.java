package org.jetbrains.postfixCompletion.infrastructure;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class PostfixItemsCompletionProvider {
  @NotNull
  public static List<LookupElement> getItems(@NotNull CompletionParameters parameters,
                                             @NotNull CompletionResultSet result,
                                             @NotNull PostfixExecutionContext executionContext) {

    PostfixTemplatesService templatesService = PostfixTemplatesService.getInstance();
    if (templatesService == null) {
      return Collections.emptyList();
    }

    PostfixTemplateContext context = templatesService.isAvailable(parameters.getPosition(), executionContext);
    if (context == null) return Collections.emptyList();

    // fix prefix mather for cases like 'xs.length == 0.f|not'
    String extraPrefix = context.shouldFixPrefixMatcher();
    if (extraPrefix != null) {
      PrefixMatcher prefixMatcher = result.getPrefixMatcher();
      PrefixMatcher extraMatcher = prefixMatcher.cloneWithPrefix(extraPrefix + prefixMatcher.getPrefix());
      result = result.withPrefixMatcher(extraMatcher);
    }

    List<LookupElement> lookupElements = templatesService.collectTemplates(context);
    for (LookupElement postfixElement : lookupElements) {
      result.addElement(postfixElement);
    }

    return lookupElements;
  }
}