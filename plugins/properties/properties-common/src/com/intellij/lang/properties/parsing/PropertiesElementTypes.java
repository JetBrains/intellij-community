// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface PropertiesElementTypes {
  @ApiStatus.Obsolete
  IStubElementType PROPERTY = new IStubElementType("PROPERTY_OLD", PropertiesLanguage.INSTANCE) {
    @Override
    public @NotNull String getExternalId() {
      return "PROPERTY_OLD";
    }

    @Override
    public void serialize(@NotNull Stub stub, @NotNull StubOutputStream dataStream) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Stub deserialize(@NotNull StubInputStream dataStream, Stub parentStub) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void indexStub(@NotNull Stub stub, @NotNull IndexSink sink) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull StubElement<?> createStub(@NotNull PsiElement psi, StubElement parentStub) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement createPsi(@NotNull StubElement stub) {
      throw new UnsupportedOperationException();
    }
  };

  @ApiStatus.Internal
  IElementType PROPERTY_TYPE = new IElementType("PROPERTY", PropertiesLanguage.INSTANCE);
  @ApiStatus.Internal
  IElementType PROPERTIES_LIST = new IElementType("PROPERTIES_LIST", PropertiesLanguage.INSTANCE);
}
