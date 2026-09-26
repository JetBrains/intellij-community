// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiLocalVariable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
open class KotlinUAnnotatedLocalVariable(
    psi: PsiLocalVariable,
    sourcePsi: KtElement,
    uastParent: UElement?,
    private val computeAnnotations: (parent: UElement) -> List<UAnnotation>
) : KotlinULocalVariable(psi, sourcePsi, uastParent) {

    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild { computeAnnotations(this) }
}
