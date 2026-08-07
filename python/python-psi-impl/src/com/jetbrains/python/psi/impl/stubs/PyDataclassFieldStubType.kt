// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub

class PyDataclassFieldStubType : CustomTargetExpressionStubType<PyDataclassFieldStub>() {

  override fun createStub(psi: PyTargetExpression): PyDataclassFieldStub? = PyDataclassFieldStubImpl.create(psi)

  override fun deserializeStub(stream: StubInputStream): PyDataclassFieldStub? = PyDataclassFieldStubImpl.deserialize(stream)
}