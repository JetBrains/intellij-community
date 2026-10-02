// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyEnumAttributeStub : CustomTargetExpressionStub {
  val literalKind: PyLiteralKind?
  val isMember: Boolean
}
