// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.stubs.PyTypedDictStub

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
class PyTypedDictStubType : CustomTargetExpressionStubType<PyTypedDictStub>() {

  override fun createStub(psi: PyTargetExpression): PyTypedDictStub? {
    return PyTypedDictStubImpl.create(psi)
  }

  @Throws(IOException::class)
  override fun deserializeStub(stream: StubInputStream): PyTypedDictStub? {
    return PyTypedDictStubImpl.deserialize(stream)
  }
}
