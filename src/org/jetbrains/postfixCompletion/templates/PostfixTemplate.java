package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
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
  @Nullable private final String myDescription;
  @Nullable private final String myExample;

  @Deprecated
  protected PostfixTemplate() {
    this(null, null, null, null);
  }

  protected PostfixTemplate(@Nullable String name, @Nullable String description, @Nullable String example) {
    this(name, null, description, example);
  }
  
  protected PostfixTemplate(@Nullable String name, @Nullable String key, @Nullable String description, @Nullable String example) {
    myPresentableName = name;
    myKey = key;
    myDescription = description;
    myExample = example;
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
    return StringUtil.notNullize(myKey, "." + getPresentableName());
  }

  @NotNull
  public String getPresentableName() {
    if (myPresentableName != null) {
      return myPresentableName;
    }
    TemplateInfo annotation = getClass().getAnnotation(TemplateInfo.class);
    return annotation.templateName();
  }

  @NotNull
  public String getDescription() {
    if (myDescription != null) {
      return myDescription;
    }
    TemplateInfo annotation = getClass().getAnnotation(TemplateInfo.class);
    return annotation.description();
  }

  @NotNull
  public String getExample() {
    if (myExample != null) {
      return myExample;
    }
    TemplateInfo annotation = getClass().getAnnotation(TemplateInfo.class);
    return annotation.example();
  }

  public boolean isEnabled() {
    final PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    return settings != null && settings.isPostfixPluginEnabled() && settings.isTemplateEnabled(this);
  }

  @Nullable
  public static PsiExpression getTopmostExpression(PsiElement context) {
    return PsiTreeUtil.getTopmostParentOfType(context, PsiExpression.class);
  }

  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    //todo: implement it in each template, make method abstract
    return false;
  }

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);
}
