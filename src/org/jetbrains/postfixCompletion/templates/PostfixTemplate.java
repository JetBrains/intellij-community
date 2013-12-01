package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.settings.PostfixCompletionSettings;

public abstract class PostfixTemplate {
  @NotNull
  public static final ExtensionPointName<PostfixTemplate> EP_NAME =
    ExtensionPointName.create("org.jetbrains.postfixCompletion.postfixTemplate");

  @Nullable
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    throw new UnsupportedOperationException();
  }
  
  //NEW API
  
  @NotNull
  public String getKey() {
    return "." + getName();
  }

  public String getName() {
    //todo: implement it in each template, remove annotations, make method abstract
    TemplateInfo annotation = getClass().getAnnotation(TemplateInfo.class);
    return annotation.templateName();
  }

  public boolean isEnabled() {
    final PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    return settings != null && settings.isTemplateEnabled(this);
  }

  public boolean isMyContext(@NotNull PsiElement context) {
    //todo: implement it in each template, make method abstract
    return false;
  }

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);
}
