package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PostfixTemplateProvider {
  void createItems(@NotNull final PostfixTemplateAcceptanceContext context, List<LookupElement> consumer);
}