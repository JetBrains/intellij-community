// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
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
    for (PsiReference reference : references) {
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
