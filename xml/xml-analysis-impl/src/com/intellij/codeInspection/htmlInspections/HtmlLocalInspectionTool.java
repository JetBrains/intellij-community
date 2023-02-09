/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkAttribute(@NotNull final XmlAttribute attribute, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkAttributeValue(@NotNull final XmlAttributeValue attributeValue, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  protected void checkText(@NotNull final XmlText text, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    // should be overridden
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
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
