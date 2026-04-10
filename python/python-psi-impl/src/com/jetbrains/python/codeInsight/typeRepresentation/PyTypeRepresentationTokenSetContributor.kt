// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeRepresentation

import com.intellij.psi.tree.TokenSet
import com.jetbrains.python.PythonDialectsTokenSetContributorBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTypeRepresentationTokenSetContributor : PythonDialectsTokenSetContributorBase() {
  override fun getExpressionTokens(): TokenSet =
    TokenSet.create(PyTypeRepresentationElementTypes.FUNCTION_SIGNATURE)
}
