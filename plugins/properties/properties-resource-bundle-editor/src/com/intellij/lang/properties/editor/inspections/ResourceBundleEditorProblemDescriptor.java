// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorProblemDescriptor implements CommonProblemDescriptor {
  private final ProblemHighlightType myHighlightType;
  private final @InspectionMessage String myDescriptionTemplate;
  private final @NotNull QuickFix @NotNull [] myFixes;

  public ResourceBundleEditorProblemDescriptor(final ProblemHighlightType type, @InspectionMessage String template, @NotNull QuickFix @NotNull ... fixes) {
    myHighlightType = type;
    myDescriptionTemplate = template;
    myFixes = fixes;
  }

  public @NotNull ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  @Override
  public @NotNull String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Override
  public @NotNull QuickFix @Nullable [] getFixes() {
    return myFixes;
  }
}
