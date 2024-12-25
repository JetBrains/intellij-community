// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

public abstract class HtmlLocalInspectionTool extends XmlSuppressableInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  protected void checkTag(final @NotNull XmlTag tag, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkAttribute(final @NotNull XmlAttribute attribute, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkAttributeValue(final @NotNull XmlAttributeValue attributeValue, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkText(final @NotNull XmlText text, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlToken(final @NotNull XmlToken token) {
        IElementType tokenType = token.getTokenType();
        if (tokenType == XmlTokenType.XML_NAME || tokenType == XmlTokenType.XML_TAG_NAME) {
          PsiElement element = token.getPrevSibling();
          while (element instanceof PsiWhiteSpace) element = element.getPrevSibling();

          if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
            PsiElement parent = element.getParent();

            if (parent instanceof XmlTag tag && !(token.getNextSibling() instanceof OuterLanguageElement)) {
              checkTag(tag, holder, isOnTheFly);
            }
          }
        }
      }

      @Override
      public void visitXmlText(@NotNull XmlText text) {
        checkText(text, holder, isOnTheFly);
      }

      @Override
      public void visitXmlAttribute(final @NotNull XmlAttribute attribute) {
        checkAttribute(attribute, holder, isOnTheFly);
      }

      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        checkAttributeValue(value, holder, isOnTheFly);
      }

    };
  }
}
