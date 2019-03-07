// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyClass

class PyDescriptorTypeImpl(source: PyClass,
                           isDefinition: Boolean,
                           override val myGetterReturnType: PyType?) : PyClassTypeImpl(source, isDefinition), PyDescriptorType {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as PyDescriptorTypeImpl

    if (myGetterReturnType != other.myGetterReturnType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + (myGetterReturnType?.hashCode() ?: 0)
    return result
  }
}