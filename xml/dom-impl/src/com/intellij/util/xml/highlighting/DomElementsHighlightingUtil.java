// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

public final class DomElementsHighlightingUtil {
  private DomElementsHighlightingUtil() {
  }

  public static @Nullable ProblemDescriptor createProblemDescriptors(final InspectionManager manager, final DomElementProblemDescriptor problemDescriptor) {
    final ProblemHighlightType type = getProblemHighlightType(problemDescriptor);
    return createProblemDescriptors(problemDescriptor, s -> manager
      .createProblemDescriptor(s.second, s.first, problemDescriptor.getDescriptionTemplate(), type, true, problemDescriptor.getFixes()));
  }

  // TODO: move it to DomElementProblemDescriptorImpl
  private static ProblemHighlightType getProblemHighlightType(final DomElementProblemDescriptor problemDescriptor) {
    if (problemDescriptor.getHighlightType() != null) {
      return problemDescriptor.getHighlightType();
    }
    if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
      final TextRange range = ((DomElementResolveProblemDescriptor)problemDescriptor).getPsiReference().getRangeInElement();
      if (range.getStartOffset() != range.getEndOffset()) {
        return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
      }
    }
    return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }

  public static @Nullable <T> T createProblemDescriptors(DomElementProblemDescriptor problemDescriptor, Function<? super Pair<TextRange, PsiElement>, ? extends T> creator) {
    final Pair<TextRange, PsiElement> range = ((DomElementProblemDescriptorImpl)problemDescriptor).getProblemRange();
    return range == DomElementProblemDescriptorImpl.NO_PROBLEM || !range.second.isPhysical() ? null : creator.fun(range);
  }

}
