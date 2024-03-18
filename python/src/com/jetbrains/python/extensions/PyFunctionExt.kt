// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.jetbrains.python.FunctionParameter
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameter

fun PyFunction.getParameter(paramToSearch: FunctionParameter): PyParameter? {
  val parameterList = this.parameterList
  val position = paramToSearch.position
  paramToSearch.name?.let { name ->
    parameterList.findParameterByName(name)?.let {
      return it
    }
  }
  if (position != FunctionParameter.POSITION_NOT_SUPPORTED) {
    val hasSelf = containingClass != null
    return parameterList.parameters.getOrNull(if (hasSelf) position + 1 else position)
  }
  return null
}
