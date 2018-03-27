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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlPathReferenceInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue value) {
        checkRefs(value, holder);
      }

      @Override
      public void visitXmlDoctype(XmlDoctype xmlDoctype) {
        checkRefs(xmlDoctype, holder);
      }

      @Override
      public void visitXmlTag(XmlTag tag) {
        checkRefs(tag, holder);
      }
    };
  }
  
  private void checkRefs(PsiElement element, ProblemsHolder holder) {
    PsiReference[] references = element.getReferences();
    for (PsiReference reference : references) {
      if (!XmlHighlightVisitor.isUrlReference(reference)) {
        continue;
      }
      if (XmlHighlightVisitor.isInjectedWithoutValidation(element)) {
        continue;
      }
      if (!needToCheckRef(reference)) {
        continue;
      }
      boolean isHtml = HtmlUtil.isHtmlTagContainingFile(element);
      if (isHtml ^ isForHtml()) {
        continue;
      }
      if (!isHtml && XmlHighlightVisitor.skipValidation(element)) {
        continue;
      }
      final TextRange range = reference.getElement() == null ? null : reference.getElement().getTextRange();
      if (range != null && !range.isEmpty() && XmlHighlightVisitor.hasBadResolve(reference, false)) {
        holder.registerProblem(reference, ProblemsHolder.unresolvedReferenceMessage(reference),
                               isHtml ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }

  protected boolean needToCheckRef(PsiReference reference) {
    return true;
  }

  protected boolean isForHtml() {
    return false;
  }
}
