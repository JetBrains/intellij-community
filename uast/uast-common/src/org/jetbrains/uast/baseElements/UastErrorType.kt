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
package org.jetbrains.uast

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor

object UastErrorType : PsiType(emptyArray()) {
    override fun getInternalCanonicalText() = "<ErrorType>"
    override fun equalsToText(text: String) = false
    override fun getCanonicalText() = internalCanonicalText
    override fun getPresentableText() = canonicalText
    override fun isValid() = false
    override fun getResolveScope() = null
    override fun getSuperTypes() = emptyArray<PsiType>()

    override fun <A : Any?> accept(visitor: PsiTypeVisitor<A>) = visitor.visitType(this)
}