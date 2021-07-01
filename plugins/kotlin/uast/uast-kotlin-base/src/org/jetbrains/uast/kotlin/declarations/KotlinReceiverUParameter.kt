// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class KotlinReceiverUParameter(
    psi: PsiParameter,
    private val receiver: KtTypeReference,
    givenParent: UElement?
) : KotlinUParameter(psi, receiver, givenParent) {

    override val uAnnotations: List<UAnnotation> by lz {
        receiver.annotationEntries
            .filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.RECEIVER }
            .map { baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this) } +
                super.uAnnotations
    }

}
