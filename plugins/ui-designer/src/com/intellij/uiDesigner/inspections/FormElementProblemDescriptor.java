// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormElementProblemDescriptor extends ProblemDescriptorBase {
  private final String myComponentId;
  private final String myPropertyName;
  public FormElementProblemDescriptor(@NotNull PsiElement element,
                                      @NotNull @InspectionMessage String descriptionTemplate,
                                      LocalQuickFix @Nullable [] fixes,
                                      @NotNull ProblemHighlightType highlightType,
                                      boolean showTooltip,
                                      boolean onTheFly,
                                      String componentId,
                                      String propertyName) {
    super(element, element, descriptionTemplate, fixes, highlightType, false, null, showTooltip, onTheFly);
    myComponentId = componentId;
    myPropertyName = propertyName;
  }

  public String getComponentId() {
    return myComponentId;
  }

  public String getPropertyName() {
    return myPropertyName;
  }
}
