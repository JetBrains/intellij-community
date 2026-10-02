// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyDataclassFieldStubType : CustomTargetExpressionStubType<PyDataclassFieldStub>() {

  override fun createStub(psi: PyTargetExpression): PyDataclassFieldStub? = PyDataclassFieldStubImpl.create(psi)

  override fun deserializeStub(stream: StubInputStream): PyDataclassFieldStub? = PyDataclassFieldStubImpl.deserialize(stream)
}