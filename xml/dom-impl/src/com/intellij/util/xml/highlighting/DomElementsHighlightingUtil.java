// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

public final class DomElementsHighlightingUtil {
  private DomElementsHighlightingUtil() {
  }

  @Nullable
  public static ProblemDescriptor createProblemDescriptors(final InspectionManager manager, final DomElementProblemDescriptor problemDescriptor) {
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

  @Nullable
  public static Annotation createAnnotation(final DomElementProblemDescriptor problemDescriptor) {

    return createProblemDescriptors(problemDescriptor, s -> {
      String text = problemDescriptor.getDescriptionTemplate();
      if (StringUtil.isEmpty(text)) text = null;
      final HighlightSeverity severity = problemDescriptor.getHighlightSeverity();

      TextRange range = s.first;
      if (text == null) range = TextRange.from(range.getStartOffset(), 0);
      range = range.shiftRight(s.second.getTextRange().getStartOffset());
      String tooltip = text == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(text));
      final Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, text, tooltip);

      if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
        annotation.setTextAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
      }

      for(LocalQuickFix fix:problemDescriptor.getFixes()) {
        if (fix instanceof IntentionAction) annotation.registerFix((IntentionAction)fix);
      }
      return annotation;
    });
  }

  @Nullable
  private static <T> T createProblemDescriptors(DomElementProblemDescriptor problemDescriptor, Function<? super Pair<TextRange, PsiElement>, ? extends T> creator) {

    final Pair<TextRange, PsiElement> range = ((DomElementProblemDescriptorImpl)problemDescriptor).getProblemRange();
    return range == DomElementProblemDescriptorImpl.NO_PROBLEM || !range.second.isPhysical() ? null : creator.fun(range);
  }

}
