// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.*;

public final class PyPostfixTemplateProvider implements PostfixTemplateProvider {

  private final @NotNull Set<PostfixTemplate> myTemplates = ContainerUtil.newHashSet(
    new PyNotPostfixTemplate(this),
    new PyParenthesizedExpressionPostfixTemplate(this),
    new PyReturnPostfixTemplate(this),
    new PyIfPostfixTemplate(this),
    new PyWhilePostfixTemplate(this),
    new PyForPostfixTemplate("for", this),
    new PyForPostfixTemplate("iter", this),
    new PyForEnumeratePostfixTemplate("fore", this),
    new PyForEnumeratePostfixTemplate("itere", this),
    new PyForEnumeratePostfixTemplate("enum", this),
    new PyIsNonePostfixTemplate(this),
    new PyIsNotNonePostfixTemplate(this),
    new PyPrintPostfixTemplate(this),
    new PyMainPostfixTemplate(this),
    new PyLenPostfixTemplate(this)
  );

  @NotNull
  @Override
  public String getId() {
    return "builtin.python";
  }

  @Override
  public @Nullable String getPresentableName() {
    return PyBundle.message("postfix.template.provider.name");
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return myTemplates;
  }

  @Override
  public @Nullable PostfixTemplateEditor createEditor(@Nullable PostfixTemplate templateToEdit) {
    if (templateToEdit == null || templateToEdit instanceof PyEditablePostfixTemplate) {
      PyPostfixTemplateEditor result = new PyPostfixTemplateEditor(this);
      result.setTemplate(templateToEdit);
      return result;
    }
    return null;
  }

  @Nullable
  @Override
  public PostfixTemplate readExternalTemplate(@NotNull String id, @NotNull String name, @NotNull Element templateElement) {
    TemplateImpl template = readExternalLiveTemplate(templateElement, this);
    if (template == null) return null;
    Set<PyPostfixTemplateExpressionCondition> conditions =
      readExternalConditions(templateElement, PyPostfixTemplateProvider::readCondition);
    boolean useTopmostExpression = readExternalTopmostAttribute(templateElement);
    return new PyEditablePostfixTemplate(id, name, template, "", conditions, useTopmostExpression, this, false /*?*/);
  }

  @Override
  public void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
    if (template instanceof PyEditablePostfixTemplate) {
      PostfixTemplatesUtils.writeExternalTemplate(template, parentElement);
    }
  }

  @Nullable
  private static PyPostfixTemplateExpressionCondition readCondition(@NotNull Element conditionElement) {
    String id = conditionElement.getAttributeValue(PostfixTemplateExpressionCondition.ID_ATTR);
    return PyPostfixTemplateExpressionCondition.PyClassCondition.ID.equals(id) ?
           PyPostfixTemplateExpressionCondition.PyClassCondition.Companion.readFrom(conditionElement) :
           PyPostfixTemplateExpressionCondition.PUBLIC_CONDITIONS.get(id);
  }


  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.'|| currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @Override
  public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @NotNull
  @Override
  public PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
    return copyFile;
  }
}
