package com.jetbrains.python.sdk

class PyPreferringCondaSdkTypeComparator: PySdkTypeComparator {

  override fun compare(type1: PySdkTypeComparator.PySdkType, type2: PySdkTypeComparator.PySdkType): Int {
    return when {
      type1 == PySdkTypeComparator.PySdkType.CondaEnv -> -1
      type2 == PySdkTypeComparator.PySdkType.CondaEnv -> 1
      else -> 0
    }
  }
}