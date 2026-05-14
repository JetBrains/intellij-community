// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyCallSiteOwner : PyElement {
  fun getReceiver(resolvedCallee: PyCallable?): PyExpression?

  fun getArguments(resolvedCallee: PyCallable?): List<PyExpression>
}
