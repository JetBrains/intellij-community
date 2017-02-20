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
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
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
    ObjectUtils.notNull(myProperty.getElement()).delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
