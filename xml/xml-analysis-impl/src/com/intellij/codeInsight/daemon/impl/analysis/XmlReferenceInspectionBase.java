// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class XmlReferenceInspectionBase extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        checkRefs(value, holder, 0);
      }

      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        int startIndex = !attribute.getNamespacePrefix().isEmpty() ? 2 : 1;
        checkRefs(attribute, holder, startIndex);
      }

      @Override
      public void visitXmlDoctype(@NotNull XmlDoctype xmlDoctype) {
        checkRefs(xmlDoctype, holder, 0);
      }

      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        checkRefs(tag, holder, 0);
      }

      @Override
      public void visitXmlProcessingInstruction(@NotNull XmlProcessingInstruction processingInstruction) {
        checkRefs(processingInstruction, holder, 0);
      }
    };
  }

  private void checkRefs(@NotNull XmlElement element, @NotNull ProblemsHolder holder, int startIndex) {
    PsiReference[] references = element.getReferences();
    if (references.length <= startIndex) {
      return;
    }

    if (XmlHighlightVisitor.isInjectedWithoutValidation(element)) {
      return;
    }
    boolean isHtml = HtmlUtil.isHtmlTagContainingFile(element);
    if (!checkHtml(element, isHtml)) {
      return;
    }

    Collection<PsiReference> unresolved = getUnresolvedReferencesToAnnotate(Arrays.copyOfRange(references, startIndex, references.length));
    for (PsiReference reference : unresolved) {
      holder.registerProblem(reference, ProblemsHolder.unresolvedReferenceMessage(reference),
                             isHtml ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
  }

  protected boolean checkHtml(@NotNull XmlElement element, boolean isHtml) {
    if (isHtml ^ isForHtml()) {
      return false;
    }
    if (!isHtml && XmlHighlightVisitor.skipValidation(element)) {
      return false;
    }
    return true;
  }

  @NotNull
  @Unmodifiable
  private Collection<PsiReference> getUnresolvedReferencesToAnnotate(PsiReference[] references) {
    Map<TextRange, PsiReference> unresolvedReferences = new HashMap<>();
    for (PsiReference reference : references) {
      if (!needToCheckRef(reference)) {
        continue;
      }

      if (!checkRanges()) {
        if (XmlHighlightVisitor.hasBadResolve(reference, false)) {
          unresolvedReferences.put(reference.getRangeInElement(), reference);
        }
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

  protected boolean checkRanges() {
    return true;
  }

  protected abstract boolean needToCheckRef(PsiReference reference);

  protected boolean isForHtml() {
    return false;
  }
}
