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
package com.jetbrains.python.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptorImpl;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Alexey.Ivanov
 */
public class UnsupportedFeatures extends CompatibilityVisitor {

  public UnsupportedFeatures() {
    super(new ArrayList<>());
  }

  @Override
  public void visitPyElement(PyElement node) {
    setVersionsToProcess(Arrays.asList(LanguageLevel.forElement(node)));
  }

  @Override
  protected void registerProblem(@Nullable final PsiElement node, String message, LocalQuickFix localQuickFix, boolean asError) {
    if (node == null) return;
    registerProblem(node, node.getTextRange(), message, localQuickFix, asError);
  }

  @Override
  protected void registerProblem(PsiElement node, TextRange range, String message, LocalQuickFix localQuickFix, boolean asError) {
    if (range.isEmpty()){
      return;
    }
    if (localQuickFix != null)
      if (asError)
        getHolder().createErrorAnnotation(range, message).registerFix(createIntention(node, message, localQuickFix));
      else
        getHolder().createWarningAnnotation(range, message).registerFix(createIntention(node, message, localQuickFix));
    else
      if (asError)
        getHolder().createErrorAnnotation(range, message);
      else
        getHolder().createWarningAnnotation(range, message);
  }

  private static IntentionAction createIntention(PsiElement node, String message, LocalQuickFix fix) {
    return createIntention(node, node.getTextRange(), message, fix);
  }

  private static IntentionAction createIntention(PsiElement node, TextRange range, String message, LocalQuickFix fix) {
    LocalQuickFix[] quickFixes = {fix};
    CommonProblemDescriptorImpl descr = new ProblemDescriptorImpl(node, node, message,
                                                                  quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
                                                                  range, true);
    return QuickFixWrapper.wrap((ProblemDescriptor)descr, 0);
  }
}
