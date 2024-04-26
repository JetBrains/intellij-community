// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

class DomElementResolveProblemDescriptorImpl extends DomElementProblemDescriptorImpl implements DomElementResolveProblemDescriptor {
  private final @NotNull PsiReference myReference;

  DomElementResolveProblemDescriptorImpl(final @NotNull GenericDomValue domElement,
                                         final @NotNull PsiReference reference,
                                         @NotNull LocalQuickFix @NotNull ... quickFixes) {
     super(domElement, reference instanceof FileReference ? ProblemsHolder.unresolvedReferenceMessage(reference) : XmlHighlightVisitor.getErrorDescription(reference), HighlightSeverity.ERROR, quickFixes);
     myReference = reference;
  }

  @Override
  public @NotNull PsiReference getPsiReference() {
    return myReference;
  }

  @Override
  public @NotNull GenericDomValue getDomElement() {
    return (GenericDomValue)super.getDomElement();
  }

  @Override
  protected @NotNull Pair<TextRange, PsiElement> computeProblemRange() {
    final PsiReference reference = myReference;
    PsiElement element = reference.getElement();
    if (element instanceof XmlAttributeValue && element.getTextLength() == 0) return NO_PROBLEM;
    TextRange referenceRange = reference.getRangeInElement();
    return Pair.create(referenceRange, element);
  }
}
