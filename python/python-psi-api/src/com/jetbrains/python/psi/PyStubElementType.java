// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The legacy base of a stub element type of a Python dialect. The Python PSI itself binds its stubs to the plain element types of
 * {@code PyElementTypes} through {@code com.jetbrains.python.psi.impl.stubs.PyStubElementFactory} and the stub registry extension.
 * The deprecated constants of {@code PyStubElementTypes} adapt those factories to this class for an external plugin.
 */
public abstract class PyStubElementType<StubT extends StubElement<?>, PsiT extends PyElement> extends IStubElementType<StubT, PsiT>
  implements PyPsiCreatingElementType {
  public PyStubElementType(@NotNull @NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }

  @Override
  public abstract @NotNull PsiElement createElement(final @NotNull ASTNode node);

  @Override
  public void indexStub(final @NotNull StubT stub, final @NotNull IndexSink sink) {
  }

  @Override
  public @NotNull String getExternalId() {
    return "py." + super.toString();
  }
}