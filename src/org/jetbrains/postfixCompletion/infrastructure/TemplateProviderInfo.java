package org.jetbrains.postfixCompletion.infrastructure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

public class TemplateProviderInfo {
  @NotNull public final PostfixTemplate provider;
  @NotNull public final TemplateInfo annotation;

  public TemplateProviderInfo(@NotNull PostfixTemplate provider, @NotNull TemplateInfo annotation) {
    this.provider = provider;
    this.annotation = annotation;
  }
}
