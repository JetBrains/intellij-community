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
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class FieldFormReference extends ReferenceInForm {
  private final PsiReference myClassReference;
  private final String myComponentClassName;
  private final TextRange myComponentClassNameRange;
  private final boolean myCustomCreate;

  public FieldFormReference(final PsiPlainTextFile file,
                            final PsiReference aClass,
                            final TextRange fieldNameRange,
                            @Nullable String componentClassName,
                            @Nullable TextRange componentClassNameRange,
                            final boolean customCreate) {
    super(file, fieldNameRange);
    myClassReference = aClass;
    myComponentClassName = componentClassName;
    myComponentClassNameRange = componentClassNameRange;
    myCustomCreate = customCreate;
  }

  public PsiElement resolve() {
    final PsiElement element = myClassReference.resolve();
    if(element instanceof PsiClass){
      return ((PsiClass)element).findFieldByName(getRangeText(), true);
    }
    return null;
  }

  @Nullable
  public String getComponentClassName() {
    return myComponentClassName;
  }

  @Nullable
  public TextRange getComponentClassNameTextRange() {
    return myComponentClassNameRange;
  }

  public boolean isCustomCreate() {
    return myCustomCreate;
  }

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException();
    }

    final PsiField field = (PsiField)element;
    if (!myClassReference.equals(field.getContainingClass())) {
      throw new IncorrectOperationException();
    }
    final String text = field.getName();
    updateRangeText(text);

    return myFile;
  }
}
