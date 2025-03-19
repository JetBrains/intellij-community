// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import org.jetbrains.annotations.Nullable;

public interface PyTypeAliasStatementStub extends NamedStub<PyTypeAliasStatement>, PyVersionSpecificStub {

  @Nullable
  String getTypeExpressionText();
}
