/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorProblemDescriptor implements ProblemDescriptor {
  private final ProblemHighlightType myHighlightType;
  private final String myDescriptionTemplate;
  private final QuickFix[] myFixes;

  public ResourceBundleEditorProblemDescriptor(final ProblemHighlightType type, String template, QuickFix<ResourceBundleEditorProblemDescriptor>... fixes) {
    myHighlightType = type;
    myDescriptionTemplate = template;
    myFixes = fixes;
  }

  @NotNull
  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  @NotNull
  @Override
  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Nullable
  @Override
  public QuickFix[] getFixes() {
    return myFixes;
  }

  @Override
  public PsiElement getPsiElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getStartElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getEndElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TextRange getTextRangeInElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLineNumber() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAfterEndOfLine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTextAttributes(TextAttributesKey key) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public ProblemGroup getProblemGroup() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setProblemGroup(@Nullable ProblemGroup problemGroup) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean showTooltip() {
    throw new UnsupportedOperationException();
  }
}
