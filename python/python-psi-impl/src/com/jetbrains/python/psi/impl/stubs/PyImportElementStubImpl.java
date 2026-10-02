// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
  private final QualifiedName myImportedQName;
  private final String myAsName;

  public PyImportElementStubImpl(@Nullable QualifiedName importedQName, String asName, final StubElement parent,
                                 IStubElementType elementType) {
    super(parent, elementType);
    myImportedQName = importedQName;
    myAsName = asName;
  }

  @Override
  public @Nullable QualifiedName getImportedQName() {
    return myImportedQName;
  }

  @Override
  public String getAsName() {
    return myAsName;
  }

  @Override
  public String toString() {
    return "PyImportElementStubImpl{" +
           "myImportedQName=" + myImportedQName +
           ", myAsName='" + myAsName + '\'' +
           '}';
  }
}
