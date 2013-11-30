package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;

public abstract class PostfixTemplate {
  @NotNull
  public static final ExtensionPointName<PostfixTemplate> EP_NAME =
    ExtensionPointName.create("org.jetbrains.postfixCompletion.postfixTemplate");

  @Nullable
  public abstract LookupElement createLookupElement(@NotNull PostfixTemplateContext context);
}
