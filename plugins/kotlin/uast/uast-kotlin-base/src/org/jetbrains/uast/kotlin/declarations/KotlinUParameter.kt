// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParameterEx
import org.jetbrains.uast.UastLazyPart

@ApiStatus.Internal
open class KotlinUParameter(
    psi: PsiParameter,
    final override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UParameterEx, PsiParameter by psi {

    private val isLightConstructorParamLazy = UastLazyPart<Boolean?>()
    private val isKtConstructorParamLazy = UastLazyPart<Boolean?>()

    final override val javaPsi: PsiParameter = unwrap<UParameter, PsiParameter>(psi)

    override val psi: PsiParameter = javaPsi

    override fun getInitializer(): PsiExpression? {
        return super<AbstractKotlinUVariable>.getInitializer()
    }

    override fun getOriginalElement(): PsiElement? {
        return super<AbstractKotlinUVariable>.getOriginalElement()
    }

    override fun getNameIdentifier(): PsiIdentifier {
        return super.getNameIdentifier()
    }

    override fun getContainingFile(): PsiFile {
        return super.getContainingFile()
    }
}
