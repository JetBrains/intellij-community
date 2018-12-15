// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
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

  @Override
  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement() {
    return new TextRange(0, myElement.getTextLength());
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myElement.getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (myElement instanceof PsiNamedElement) {
      return ((PsiNamedElement)myElement).setName(newElementName);
    }
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  @Override
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
