// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.stdlib

import com.intellij.psi.PsiReference
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType
import com.jetbrains.python.codeInsight.stdlib.PyStdlibClassMembersProvider
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyStdlibInspectionExtension : PyInspectionExtension() {

  override fun ignoreInitNewSignatures(original: PyFunction, complementary: PyFunction): Boolean {
    return PyNames.TYPE_ENUM == complementary.containingClass?.qualifiedName
  }

  override fun ignoreUnresolvedMember(type: PyType, name: String, context: TypeEvalContext): Boolean {
    if (type is PyClassLikeType) {
      return type is PyNamedTupleType && type.fields.containsKey(name) ||
             type.getAncestorTypes(context).filterIsInstance<PyNamedTupleType>().any { it.fields.containsKey(name) }
    }

    return false
  }

  override fun ignoreUnresolvedReference(node: PyElement, reference: PsiReference, context: TypeEvalContext): Boolean {
    if (node is PyReferenceExpression && node.isQualified) {
      val qualifier = node.qualifier
      if (qualifier is PyReferenceExpression) {
        return PyStdlibClassMembersProvider.referenceToMockPatch(qualifier, context) &&
               PyStdlibClassMembersProvider.MOCK_PATCH_MEMBERS.find { it.name == node.name } != null
      }
    }

    return false
  }
}