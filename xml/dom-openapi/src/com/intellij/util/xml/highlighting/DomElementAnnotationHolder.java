// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DomElementAnnotationHolder extends Iterable<DomElementProblemDescriptor> {

  boolean isOnTheFly();

  @NotNull
  DomFileElement<?> getFileElement();

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            LocalQuickFix... fixes);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            DomCollectionChildDescription childDescription,
                                            @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String message);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @Nls(capitalization = Nls.Capitalization.Sentence) String message);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            LocalQuickFix... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            TextRange textRange,
                                            LocalQuickFix... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            ProblemHighlightType highlightType,
                                            @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            @Nullable TextRange textRange,
                                            LocalQuickFix... fixes);

  @NotNull
  DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference);

  /**
   * Is useful only if called from {@link DomElementsAnnotator} instance.
   */
  @NotNull
  Annotation createAnnotation(@NotNull DomElement element, HighlightSeverity severity, @Nullable String message);

  int getSize();
}
