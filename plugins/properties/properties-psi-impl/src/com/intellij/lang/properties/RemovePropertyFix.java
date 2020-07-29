// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class RemovePropertyFix implements IntentionAction {
  private final SmartPsiElementPointer<Property> myProperty;

  RemovePropertyFix(@NotNull final Property origProperty) {
    myProperty = SmartPointerManager.getInstance(origProperty.getProject()).createSmartPsiElementPointer(origProperty);
  }

  @Override
  @NotNull
  public String getText() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file != null &&
           file.isValid() &&
           PsiManager.getInstance(project).isInProject(file) &&
           myProperty.getElement() != null;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myProperty.getElement();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Objects.requireNonNull(myProperty.getElement()).delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
