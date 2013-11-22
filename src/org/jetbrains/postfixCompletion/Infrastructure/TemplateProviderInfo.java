package org.jetbrains.postfixCompletion.Infrastructure;

import org.jetbrains.annotations.*;

public final class TemplateProviderInfo {
  @NotNull public final PostfixTemplateProvider provider;
  @NotNull public final TemplateProvider annotation;

  public TemplateProviderInfo(
    @NotNull PostfixTemplateProvider provider, @NotNull TemplateProvider annotation) {
    this.provider = provider;
    this.annotation = annotation;
  }
}
