package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.settings.PostfixCompletionSettings;

public abstract class PostfixTemplate {
  @Nullable private final String myPresentableName;
  @Nullable private final String myKey;

  @Deprecated
  protected PostfixTemplate() {
    this(null, null);
  }

  protected PostfixTemplate(@Nullable String name) {
    this(name, null);
  }
  
  protected PostfixTemplate(@Nullable String name, @Nullable String key) {
    myPresentableName = name;
    myKey = key;
  }

  @NotNull
  public static final ExtensionPointName<PostfixTemplate> EP_NAME =
    ExtensionPointName.create("org.jetbrains.postfixCompletion.postfixTemplate");

  @Nullable
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    throw new UnsupportedOperationException();
  }
  
  //NEW API
  
  @NotNull
  public final String getKey() {
    return StringUtil.notNullize(myKey,  "." + getPresentableName());
  }

  @NotNull
  public String getPresentableName() {
    if (myPresentableName != null) {
      return myPresentableName;
    }
    TemplateInfo annotation = getClass().getAnnotation(TemplateInfo.class);
    return annotation.templateName();
  }

  public boolean isEnabled() {
    final PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    return settings != null && settings.isTemplateEnabled(this);
  }

  @Nullable
  public PsiExpression getTopmostExpression(PsiElement context) {
    return PsiTreeUtil.getTopmostParentOfType(context, PsiExpression.class);
  }

  public boolean isApplicable(@NotNull PsiElement context) {
    //todo: implement it in each template, make method abstract
    return false;
  }

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);
}
