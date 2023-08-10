/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;

public class UnsupportedFeatures extends CompatibilityVisitor {

  public UnsupportedFeatures() {
    super(new ArrayList<>());
  }

  @Override
  public synchronized void annotateElement(PsiElement psiElement, AnnotationHolder holder) {
    setVersionsToProcess(Collections.singletonList(LanguageLevel.forElement(psiElement)));
    super.annotateElement(psiElement, holder);
  }

  @Override
  protected void registerProblem(@NotNull PsiElement node,
                                 @NotNull TextRange range,
                                 @NotNull String message,
                                 boolean asError,
                                 LocalQuickFix @NotNull ... fixes) {
    if (range.isEmpty()) {
      return;
    }

    HighlightSeverity severity = asError ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
    if (fixes.length > 0) {
      AnnotationBuilder annotationBuilder = getHolder().newAnnotation(severity, message).range(range);
      for (LocalQuickFix fix: fixes) {
        if (fix != null) {
          annotationBuilder = annotationBuilder.withFix(createIntention(node, message, fix));
        }
      }
      annotationBuilder.create();
    }
    else {
      getHolder().newAnnotation(severity, message).range(range).create();
    }
  }

  @NotNull
  private static IntentionAction createIntention(@NotNull PsiElement node, @NotNull @InspectionMessage String message, @NotNull LocalQuickFix localQuickFix) {
    return createIntention(node, null, message, localQuickFix);
  }

  @NotNull
  private static IntentionAction createIntention(@NotNull PsiElement node,
                                                 @Nullable TextRange range,
                                                 @NotNull @InspectionMessage String message,
                                                 @NotNull LocalQuickFix localQuickFix) {
    final LocalQuickFix[] quickFixes = {localQuickFix};
    ProblemDescriptor descr =
      new ProblemDescriptorImpl(node, node, message, quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, range, true);

    return QuickFixWrapper.wrap(descr, 0);
  }
}
