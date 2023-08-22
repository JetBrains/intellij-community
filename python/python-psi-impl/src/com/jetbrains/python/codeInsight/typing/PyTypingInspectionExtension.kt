/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.typing

import com.intellij.psi.PsiReference
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypingInspectionExtension : PyInspectionExtension() {

  override fun ignoreUnresolvedReference(node: PyElement, reference: PsiReference, context: TypeEvalContext): Boolean {
    if (node is PySubscriptionExpression && reference is PyOperatorReference && node.referencedName == PyNames.GETITEM) {
      val operand = node.operand
      val type = context.getType(operand)

      if (type is PyClassLikeType && type.isDefinition) {
        if (isGenericItselfOrDescendant(type, context)) {
          // `true` is not returned for the cases like `typing.List[int]`
          // because these types contain builtins as a class
          if (!isBuiltin(type)) return true

          // here is the check that current element is like `typing.List[int]`
          // but be careful: builtin collections inherit `typing.Generic` in typeshed
          if (operand is PyReferenceExpression) {
            val resolveContext = PyResolveContext.defaultContext(context)
            val resolveResults = operand.getReference(resolveContext).multiResolve(false)

            if (resolveResults
                .asSequence()
                .map { it.element }
                .any { it is PyTargetExpression && PyTypingTypeProvider.BUILTIN_COLLECTION_CLASSES.containsKey(it.qualifiedName) }) {
              return true
            }
          }
        }
        if (PyBuiltinCache.getInstance(operand).typeType?.toClass() == type) {
          return true
        }
      }
    }

    return false
  }

  private fun isGenericItselfOrDescendant(type: PyClassLikeType, context: TypeEvalContext): Boolean {
    return PyTypingTypeProvider.GENERIC_CLASSES.contains(type.classQName) || PyTypingTypeProvider.isGeneric(type, context)
  }

  private fun isBuiltin(type: PyClassLikeType): Boolean {
    return if (type is PyClassType) PyBuiltinCache.getInstance(type.pyClass).isBuiltin(type.pyClass) else false
  }
}

