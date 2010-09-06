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
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DomElementAnnotationHolder extends Iterable<DomElementProblemDescriptor>{

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, @Nullable String message, LocalQuickFix... fixes);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message);

  @NotNull
  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, HighlightSeverity highlightType, String message);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, HighlightSeverity highlightType, String message, LocalQuickFix... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, HighlightSeverity highlightType, String message, TextRange textRange, LocalQuickFix... fixes);

  DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, ProblemHighlightType highlightType, String message, TextRange textRange, LocalQuickFix... fixes);

  @NotNull
  DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference);

  /**
   * Is useful only if called from {@link com.intellij.util.xml.highlighting.DomElementsAnnotator} instance
   * @param element element
   * @param severity highlight severity
   * @param message description
   * @return annotation
   */
  @NotNull
  Annotation createAnnotation(@NotNull DomElement element, HighlightSeverity severity, @Nullable String message);

  int getSize();
}
