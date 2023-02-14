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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
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

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final @InspectionMessage String message,
                                       @NotNull final LocalQuickFix quickFix) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }
}
