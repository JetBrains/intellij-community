package com.jetbrains.python.psi.types

interface PyCompoundType : PyType {
  val members: Collection<PyType?>
}