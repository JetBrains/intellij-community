// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyThirdPartyInspectionExtension : PyInspectionExtension() {

  override fun ignoreMethodParameters(function: PyFunction, context: TypeEvalContext): Boolean {
    val cls = function.containingClass
    if (cls != null) {
      // zope.interface.Interface inheritor could have any parameters
      val interfaceQName = "zope.interface.interface.Interface"
      if (cls.isSubclass(interfaceQName, context)) return true

      // Checking for subclassing above does not help while zope.interface.Interface is defined as target with call expression assigned
      val resolveContext = PyResolveContext.defaultContext(context)
      for (expression in cls.superClassExpressions) {
        if (resolvesTo(expression, interfaceQName, resolveContext)) return true
      }
    }

    return false
  }

  override fun ignoreUnresolvedMember(type: PyType, name: String, context: TypeEvalContext): Boolean {
    return name == "__attrs_attrs__" &&
           type is PyClassType &&
           parseDataclassParameters(type.pyClass, context)?.type?.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS
  }

  private fun resolvesTo(expression: PyExpression, qualifiedName: String, resolveContext: PyResolveContext): Boolean {
    return ContainerUtil.exists(PyUtil.multiResolveTopPriority(expression, resolveContext)) {
      it is PyElement && QualifiedNameFinder.getQualifiedName(it) == qualifiedName
    }
  }
}
