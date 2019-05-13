/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.rest.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspectionVisitor extends RestElementVisitor {
  @Nullable private final ProblemsHolder myHolder;
  public RestInspectionVisitor(@Nullable final ProblemsHolder holder) {
    myHolder = holder;
  }

  public RestInspectionVisitor(@Nullable ProblemsHolder problemsHolder,
                               @NotNull LocalInspectionToolSession session) {
    myHolder = problemsHolder;
  }

  @Nullable protected ProblemsHolder getHolder() {
    return myHolder;
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message){
    if (element == null || element.getTextLength() == 0){
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message);
    }
  }

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       @NotNull final LocalQuickFix quickFix){
      if (element == null || element.getTextLength() == 0){
          return;
      }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }


  /**
   * The most full-blown version.
   * @see com.intellij.codeInspection.ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull final PsiElement psiElement,
    @NotNull final String descriptionTemplate,
    final ProblemHighlightType highlightType,
    @Nullable final HintAction hintAction,
    final LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, fixes);
  }

  /**
   * The most full-blown version.
   * @see com.intellij.codeInspection.ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull final PsiElement psiElement,
    @NotNull final String descriptionTemplate,
    final ProblemHighlightType highlightType,
    @Nullable final HintAction hintAction,
    @Nullable final TextRange rangeInElement,
    final LocalQuickFix... fixes)
  {
    if (myHolder != null) {
      myHolder.registerProblem(new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false,
                                                         rangeInElement, hintAction, myHolder.isOnTheFly()));
    }
  }
}
