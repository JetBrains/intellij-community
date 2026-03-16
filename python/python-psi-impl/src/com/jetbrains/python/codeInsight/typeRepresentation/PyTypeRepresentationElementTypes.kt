/*
 * Copyright 2000-2025 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.typeRepresentation

import com.intellij.psi.tree.IElementType
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyNamedParameterTypeRepresentation
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyParameterListRepresentation
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.impl.PyElementImpl

object PyTypeRepresentationElementTypes {
  val FUNCTION_SIGNATURE: PyElementType = PyElementType("FUNCTION_SIGNATURE") { node -> PyFunctionTypeRepresentation(node) }
  val PARAMETER_TYPE_LIST: PyElementType = PyElementType("PARAMETER_TYPE_LIST") { node -> PyParameterListRepresentation(node) }
  val NAMED_PARAMETER_TYPE: PyElementType = PyElementType("NAMED_PARAMETER_TYPE") { node -> PyNamedParameterTypeRepresentation(node) }
  val PLACEHOLDER: IElementType = PyElementType("PLACEHOLDER") { node -> PyElementImpl(node) }
}
