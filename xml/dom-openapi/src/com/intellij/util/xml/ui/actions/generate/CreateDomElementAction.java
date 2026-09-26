// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateDomElementAction<T extends DomElement> extends SimpleCodeInsightAction {

  private final Class<? extends T> myContextClass;

  public CreateDomElementAction(Class<? extends T> contextClass) {
    myContextClass = contextClass;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    createElement(getContextElement(editor), editor, psiFile, project);
  }

  protected abstract void createElement(T context, Editor editor, PsiFile file, Project project);

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return getContextElement(editor) != null;
  }

  protected @Nullable T getContextElement(Editor editor) {
    return DomUtil.getContextElement(editor, myContextClass);
  }

  public static void replaceElementValue(TemplateBuilder builder, GenericDomValue element, Expression expression) {
    element.setStringValue("");
    XmlElement xmlElement = element.getXmlElement();
    builder.replaceElement(xmlElement, ElementManipulators.getValueTextRange(xmlElement), expression);
  }
}
