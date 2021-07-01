// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiLocalVariable
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

open class KotlinUAnnotatedLocalVariable(
    psi: PsiLocalVariable,
    sourcePsi: KtElement,
    uastParent: UElement?,
    computeAnnotations: (parent: UElement) -> List<UAnnotation>
) : KotlinULocalVariable(psi, sourcePsi, uastParent) {

    override val uAnnotations: List<UAnnotation> by lz { computeAnnotations(this) }
}
