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
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.impl.PyOverridingTypeProvider
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

class PyStdlibOverridingTypeProvider : PyTypeProviderBase(), PyOverridingTypeProvider {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): PyType? {
    return PyStdlibTypeProvider.getNamedTupleTypeForResolvedCallee(referenceTarget, context, anchor) ?:
           PyStdlibTypeProvider.getNamedTupleReplaceType(referenceTarget, context, anchor)
  }
}