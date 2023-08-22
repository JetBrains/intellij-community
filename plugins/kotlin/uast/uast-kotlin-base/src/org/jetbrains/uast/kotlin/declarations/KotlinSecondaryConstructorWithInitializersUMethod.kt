// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UElement

// This class was created as a workaround for KT-21617 to be the only constructor which includes `init` block
// when there is no primary constructors in the class.
// It is expected to have only one constructor of this type in a UClass.
@ApiStatus.Internal
class KotlinSecondaryConstructorWithInitializersUMethod(
    ktClass: KtClassOrObject?,
    psi: KtLightMethod,
    givenParent: UElement?
) : KotlinConstructorUMethod(ktClass, psi, psi.kotlinOrigin, givenParent) {

    override fun getBodyExpressions(): List<KtExpression> {
        return getInitializers() + super.getBodyExpressions()
    }
}
