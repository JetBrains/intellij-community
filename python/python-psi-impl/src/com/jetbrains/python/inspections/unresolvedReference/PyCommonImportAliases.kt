// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unresolvedReference

import org.jetbrains.annotations.ApiStatus

@JvmField
@ApiStatus.Internal val PY_COMMON_IMPORT_ALIASES = mapOf(
  "np" to "numpy",
  "pl" to "pylab",
  "p" to "pylab",
  "sp" to "scipy",
  "pd" to "pandas",
  "sym" to "sympy",
  "sm" to "statmodels",
  "nx" to "networkx",
  "sk" to "sklearn",

  "plt" to "matplotlib.pyplot",
  "mpimg" to "matplotlib.image",
  "mimg" to "matplotlib.image",
)