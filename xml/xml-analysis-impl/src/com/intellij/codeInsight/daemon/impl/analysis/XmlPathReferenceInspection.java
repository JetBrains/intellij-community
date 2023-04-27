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
import com.intellij.openapi.paths.PathReferenceManagerImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlPathReferenceInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        checkRefs(value, holder);
      }

      @Override
      public void visitXmlDoctype(@NotNull XmlDoctype xmlDoctype) {
        checkRefs(xmlDoctype, holder);
      }

      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        checkRefs(tag, holder);
      }
    };
  }

  private void checkRefs(@NotNull XmlElement element, @NotNull ProblemsHolder holder) {
    PsiReference[] references = element.getReferences();
    if (references.length == 0) {
      return;
    }
    if (XmlHighlightVisitor.isInjectedWithoutValidation(element)) {
      return;
    }
    boolean isHtml = HtmlUtil.isHtmlTagContainingFile(element);
    if (isHtml ^ isForHtml()) {
      return;
    }
    if (!isHtml && XmlHighlightVisitor.skipValidation(element)) {
      return;
    }

    Collection<PsiReference> unresolved = getUnresolvedReferencesToAnnotate(references);
    for (PsiReference reference : unresolved) {
      holder.registerProblem(reference, ProblemsHolder.unresolvedReferenceMessage(reference),
                             isHtml ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
  }

  @NotNull
  private Collection<PsiReference> getUnresolvedReferencesToAnnotate(PsiReference[] references) {
    Map<TextRange, PsiReference> unresolvedReferences = new HashMap<>();
    for (PsiReference reference : ContainerUtil.concat(references, PathReferenceManagerImpl::remergeWrappedReferences)) {
      if (!XmlHighlightVisitor.isUrlReference(reference) || !needToCheckRef(reference)) {
        continue;
      }

      TextRange elementRange = reference.getElement().getTextRange();
      if (elementRange == null || elementRange.isEmpty()) {
        continue;
      }

      TextRange rangeInElement = reference.getRangeInElement();
      if (unresolvedReferences.containsKey(rangeInElement) && unresolvedReferences.get(rangeInElement) == null) {
        continue;
      }

      if (XmlHighlightVisitor.hasBadResolve(reference, true)) {
        if (!reference.isSoft()) {
          unresolvedReferences.putIfAbsent(rangeInElement, reference);
        }
      }
      else {
        // the specific range has at least one resolved reference
        // no need to check any other references from that range
        unresolvedReferences.put(rangeInElement, null);
      }
    }

    return ContainerUtil.skipNulls(unresolvedReferences.values());
  }

  protected boolean needToCheckRef(PsiReference reference) {
    return true;
  }

  protected boolean isForHtml() {
    return false;
  }
}
