// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUAnnotationMethod(
    psi: KtLightMethod,
    givenParent: UElement?
) : KotlinUMethod(psi, psi.kotlinOrigin, givenParent), UAnnotationMethod {

    private val uastDefaultValuePart = UastLazyPart<UExpression?>()

    override val psi: KtLightMethod = unwrap<UMethod, KtLightMethod>(psi)

    override val uastDefaultValue: UExpression?
        get() = uastDefaultValuePart.getOrBuild {
            val annotationParameter = sourcePsi as? KtParameter ?: return@getOrBuild null
            val defaultValue = annotationParameter.defaultValue ?: return@getOrBuild null
            languagePlugin?.convertElement(defaultValue, this) as? UExpression
        }
}
