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
package com.jetbrains.python.inspections.stdlib

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFunction
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
}