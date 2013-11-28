package org.jetbrains.postfixCompletion.infrastructure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.templates.PostfixTemplateProvider;

public final class TemplateProviderInfo {
  @NotNull public final PostfixTemplateProvider provider;
  @NotNull public final TemplateProvider annotation;

  public TemplateProviderInfo(
    @NotNull PostfixTemplateProvider provider, @NotNull TemplateProvider annotation) {
    this.provider = provider;
    this.annotation = annotation;
  }
}
