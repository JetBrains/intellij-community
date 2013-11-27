package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;

import java.util.List;

public abstract class PostfixTemplateProvider {
  public static final ExtensionPointName<PostfixTemplateProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.postfixCompletion.templateProvider");
  
  public abstract void createItems(@NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer);
}
