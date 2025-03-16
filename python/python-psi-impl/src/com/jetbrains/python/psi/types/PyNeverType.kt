package com.jetbrains.python.psi.types

object PyNeverType: PyUnionType(LinkedHashSet()) {
  override fun getName(): String = "Never"
}