package com.jetbrains.python.inspections.unresolvedReference

@JvmField
val PY_COMMON_IMPORT_ALIASES = mapOf(
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