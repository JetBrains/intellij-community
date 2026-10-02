// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PyLazyParseablePsiElement extends LazyParseablePsiElement implements PyElement {

  public PyLazyParseablePsiElement(@NotNull IElementType type,
                                   @Nullable CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(this));
    if (visitor instanceof PyElementVisitor) {
      acceptPyVisitor(((PyElementVisitor)visitor));
    }
    else {
      super.accept(visitor);
    }
  }

  protected void acceptPyVisitor(@NotNull PyElementVisitor pyVisitor) {
    pyVisitor.visitPyElement(this);
  }

  @Override
  public @NotNull PythonLanguage getLanguage() {
    return (PythonLanguage)PythonFileType.INSTANCE.getLanguage();
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return PyBaseElementImpl.findReferenceAt(this, offset);
  }
}
