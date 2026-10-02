// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.openapi.util.Version;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class PyImportStatementStubImpl extends PyVersionSpecificStubBase<PyImportStatement> implements PyImportStatementStub {
  public PyImportStatementStubImpl(StubElement parentStub, IStubElementType elementType, @NotNull RangeSet<Version> versions) {
    super(parentStub, elementType, versions);
  }

  @Override
  public String toString() {
    return "PyImportStatementStub()";
  }
}
