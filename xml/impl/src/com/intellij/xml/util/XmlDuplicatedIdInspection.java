/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlDuplicatedIdInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(final XmlAttributeValue value) {
        if (value.getTextRange().isEmpty()) {
          return;
        }
        final PsiFile file = value.getContainingFile();
        if (!(file instanceof XmlFile)) {
          return;
        }
        final XmlRefCountHolder refHolder = XmlRefCountHolder.getRefCountHolder(value);
        if (refHolder == null) return;

        final PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) return;

        final XmlTag tag = (XmlTag)parent.getParent();
        if (tag == null) return;

        if (refHolder.isValidatable(tag.getParent()) && refHolder.isDuplicateIdAttributeValue(value)) {
          holder.registerProblem(value, XmlErrorMessages.message("duplicate.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }

        String idRef = XmlHighlightVisitor.getUnquotedValue(value, tag);

        if (tag instanceof HtmlTag) {
          idRef = idRef.toLowerCase();
        }

        if (XmlUtil.isSimpleXmlAttributeValue(idRef, value) && refHolder.isIdReferenceValue(value)) {
          boolean hasIdDeclaration = refHolder.hasIdDeclaration(idRef);
          if (!hasIdDeclaration && tag instanceof HtmlTag) {
            hasIdDeclaration = refHolder.hasIdDeclaration(StringUtil.stripQuotesAroundValue(value.getText()));
          }

          if (!hasIdDeclaration) {
            for(XmlIdContributor contributor: Extensions.getExtensions(XmlIdContributor.EP_NAME)) {
              if (contributor.suppressExistingIdValidation((XmlFile)file)) {
                return;
              }
            }

            final FileViewProvider viewProvider = tag.getContainingFile().getViewProvider();
            if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
              holder.registerProblem(value, XmlErrorMessages.message("invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                     new XmlDeclareIdInCommentAction(idRef));

            }
            else {
              holder.registerProblem(value, XmlErrorMessages.message("invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            }
          }
        }
      }
    };
  }

  public boolean runForWholeFile() {
    return false;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlBundle.message("xml.inspections.group.name");
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.duplicate.id");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "XmlDuplicatedId";
  }
}
