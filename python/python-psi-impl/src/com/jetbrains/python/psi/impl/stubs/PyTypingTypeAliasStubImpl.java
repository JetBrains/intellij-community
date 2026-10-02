// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyTypingAliasStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class PyTypingTypeAliasStubImpl implements PyTypingAliasStub {
  private final String myText;

  public PyTypingTypeAliasStubImpl(@NotNull String text) {
    myText = text;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull Class<PyTypingAliasStubType> getTypeClass() {
    return PyTypingAliasStubType.class;
  }

  @Override
  public void serialize(@NotNull StubOutputStream stream) throws IOException {
    stream.writeName(myText);
  }

  @Override
  public @Nullable QualifiedName getCalleeName() {
    return null;
  }

  @Override
  public String toString() {
    return "PyTypingAliasStub(text='" + getText() + "')";
  }
}
