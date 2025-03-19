// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class KotlinReceiverUParameter(
    psi: PsiParameter,
    private val receiver: KtTypeReference,
    givenParent: UElement?
) : KotlinUParameter(psi, receiver, givenParent) {

    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            receiver.annotationEntries
                .filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.RECEIVER }
                .map { baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this) } +
                    super.uAnnotations
        }
}
