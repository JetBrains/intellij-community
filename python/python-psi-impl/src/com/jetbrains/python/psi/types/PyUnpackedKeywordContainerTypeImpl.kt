package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe

internal class PyUnpackedKeywordContainerTypeImpl : PyUnpackedKeywordContainerType {
  private val myOriginalParameters: List<PyCallableParameter>
  private val myWrapperType: PyType

  constructor(originalParameters: List<PyCallableParameter>, wrapperType: PyType) {
    this.myOriginalParameters = originalParameters
    this.myWrapperType = wrapperType
  }

  override fun getUnpackedParameters(): List<PyCallableParameter?> {
    return myOriginalParameters
  }

  override fun getWrapperType(): PyType {
    return myWrapperType
  }

  override fun getName(): @NlsSafe String {
    return "Unpack[" + myWrapperType.name + "]"
  }
}
