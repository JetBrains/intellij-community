// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class LightNamedElement extends LightElement implements PyElement, PsiNamedElement {
  protected final String myName;

  public LightNamedElement(PsiManager manager, Language language, final String name) {
    super(manager, language);
    myName = name;
  }

  @Override
  public String getText() {
    return myName;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("LightNamedElement#setName() is not supported");
  }

  @Override
  public String toString() {
    return "LightNamedElement(" + myName + ")";
  }
}
