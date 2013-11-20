package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixItemsCompletionProvider {
  @NotNull public static List<LookupElement> getItems(
      @NotNull CompletionParameters parameters, @NotNull CompletionResultSet result,
      @NotNull PostfixExecutionContext executionContext) {

    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager templatesManager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement position = parameters.getPosition();

    PostfixTemplateContext context = templatesManager.isAvailable(position, executionContext);
    if (context == null) return Collections.emptyList();

    // fix prefix mather for cases like 'xs.length == 0.f|not'
    String extraPrefix = context.shouldFixPrefixMatcher();
    if (extraPrefix != null) {
      PrefixMatcher prefixMatcher = result.getPrefixMatcher();
      PrefixMatcher extraMatcher = prefixMatcher.cloneWithPrefix(extraPrefix + prefixMatcher.getPrefix());
      result = result.withPrefixMatcher(extraMatcher);
    }

    List<LookupElement> lookupElements = templatesManager.collectTemplates(context);
    for (LookupElement postfixElement : lookupElements) {
      result.addElement(postfixElement);
    }

    return lookupElements;
  }
}