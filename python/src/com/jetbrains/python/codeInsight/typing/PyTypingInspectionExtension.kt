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

import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypingInspectionExtension : PyInspectionExtension() {

  override fun ignoreUnresolvedMember(type: PyType, name: String, context: TypeEvalContext): Boolean {
    return name == PyNames.GETITEM &&
           type is PyClassLikeType &&
           type.isDefinition &&
           !isBuiltin(type) &&
           isGenericItselfOrDescendant(type, context)
  }

  private fun isGenericItselfOrDescendant(type: PyClassLikeType,
                                          context: TypeEvalContext): Boolean {
    return PyTypingTypeProvider.GENERIC_CLASSES.contains(type.classQName) ||
           type.getAncestorTypes(context).any { PyTypingTypeProvider.GENERIC_CLASSES.contains(it.classQName) }
  }

  private fun isBuiltin(type: PyClassLikeType): Boolean {
    return if (type is PyClassType) PyBuiltinCache.getInstance(type.pyClass).isBuiltin(type.pyClass) else false
  }
}

