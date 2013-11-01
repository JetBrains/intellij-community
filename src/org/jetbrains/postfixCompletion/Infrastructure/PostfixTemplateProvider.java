package org.jetbrains.postfixCompletion.Infrastructure;

import org.jetbrains.annotations.NotNull;

public interface PostfixTemplateProvider {
  void createItems(@NotNull final PostfixTemplateAcceptanceContext context);
}