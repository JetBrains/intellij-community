package com.jetbrains.python.psi.stubs

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub

interface PyEnumAttributeStub : CustomTargetExpressionStub {
  val literalKind: PyLiteralKind?
  val isMember: Boolean
}
