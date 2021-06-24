/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.uast.kotlin.declarations

import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.kotlin.BaseKotlinUAnnotationMethod
import org.jetbrains.uast.kotlin.lz

class FirKotlinUAnnotationMethod(
    psi: KtLightMethod,
    givenParent: UElement?
) : BaseKotlinUAnnotationMethod(psi, givenParent), FirKotlinUMethodParametersProducer {

    override val uastParameters by lz { produceUastParameters(this, receiverTypeReference) }
}
