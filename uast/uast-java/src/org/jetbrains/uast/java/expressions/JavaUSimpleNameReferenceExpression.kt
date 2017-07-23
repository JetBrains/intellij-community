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
package org.jetbrains.uast.java

import com.intellij.psi.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class JavaUSimpleNameReferenceExpression(
        override val psi: PsiElement?,
        override val identifier: String,
        override val uastParent: UElement?,
        val reference: PsiReference? = null 
) : JavaAbstractUExpression(), USimpleNameReferenceExpression {
    override fun resolve() = (reference ?: psi as? PsiReference)?.resolve()
    override val resolvedName: String?
        get() = ((reference ?: psi as? PsiReference)?.resolve() as? PsiNamedElement)?.name
}

class JavaUTypeReferenceExpression(
        override val psi: PsiTypeElement,
        override val uastParent: UElement?
) : JavaAbstractUExpression(), UTypeReferenceExpression {
    override val type: PsiType
        get() = psi.type
}

class LazyJavaUTypeReferenceExpression(
        override val psi: PsiElement,
        override val uastParent: UElement?,
        private val typeSupplier: () -> PsiType
) : JavaAbstractUExpression(), UTypeReferenceExpression {
    override val type: PsiType by lz { typeSupplier() }
}

class JavaClassUSimpleNameReferenceExpression(
        override val identifier: String,
        val ref: PsiJavaReference,
        override val psi: PsiElement,
        override val uastParent: UElement?
) : JavaAbstractUExpression(), USimpleNameReferenceExpression {
    override fun resolve() = ref.resolve()
    override val resolvedName: String?
        get() = (ref.resolve() as? PsiNamedElement)?.name
}