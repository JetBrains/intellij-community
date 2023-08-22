// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface DomElementAnnotationHolder extends Iterable<DomElementProblemDescriptor> {

  boolean isOnTheFly();

  @NotNull
  DomFileElement<?> getFileElement();

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            @Nullable @InspectionMessage String message,
                                            @NotNull LocalQuickFix @NotNull ... fixes);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            DomCollectionChildDescription childDescription,
                                            @Nullable @InspectionMessage String message);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @InspectionMessage String message);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @InspectionMessage String message,
                                            @NotNull LocalQuickFix @NotNull ... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            HighlightSeverity highlightType,
                                            @InspectionMessage String message,
                                            TextRange textRange,
                                            @NotNull LocalQuickFix @NotNull ... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                            ProblemHighlightType highlightType,
                                            @InspectionMessage String message,
                                            @Nullable TextRange textRange,
                                            @NotNull LocalQuickFix @NotNull ... fixes);

  @NotNull
  DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference);

  @NotNull
  AnnotationHolder getAnnotationHolder();

  int getSize();
}
