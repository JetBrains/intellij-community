// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.TypeEvalContext

object PyConstructorSignatureUtil {
  @JvmStatic
  fun findComplementaryConstructors(function: PyFunction, context: TypeEvalContext): List<PyFunction> {
    val containingClass = PyUtil.turnConstructorIntoClass(function) ?: return emptyList()
    val complementaryName = if (PyUtil.isNewMethod(function)) PyNames.INIT else PyNames.NEW
    val complementaryMethods = containingClass.multiFindMethodByName(complementaryName, true, context)

    for (complementaryMethod in complementaryMethods) {
      val complementaryMethodClass = complementaryMethod.containingClass
      if (complementaryMethodClass == null ||
          PyUtil.isObjectClass(complementaryMethodClass) ||
          PyInspectionExtension.EP_NAME.extensionList.any { it.ignoreInitNewSignatures(function, complementaryMethod) }) {
        return emptyList()
      }
    }

    return complementaryMethods
  }
}