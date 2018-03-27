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
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexei Orischenko
 */
public abstract class BaseReference implements PsiReference, PyUserInitiatedResolvableReference {
  protected final PsiElement myElement;

  protected BaseReference(@NotNull PsiElement element) {
    myElement = element;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, myElement.getTextLength());
  }

  @NotNull
  public String getCanonicalText() {
    return myElement.getText();
  }

  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return resolve() == element;
  }

  public boolean isSoft() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement userInitiatedResolve() {
    // Override this method if your reference may benefit from this knowledge
    return resolve();
  }
}
