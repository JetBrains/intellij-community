// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

internal class UastFakeSourceLightDefaultAccessor(
    original: KtProperty,
    containingClass: PsiClass,
    isSetter: Boolean,
) : UastFakeSourceLightAccessorBase<KtProperty>(
    original,
    original,
    containingClass,
    isSetter
) {
    private val parameterListPart = UastLazyPart<PsiParameterList>()

    private val _parameterList: PsiParameterList
        get() = parameterListPart.getOrBuild {
            object : LightParameterListBuilder(original.manager, original.language) {
                override fun getParent(): PsiElement = this@UastFakeSourceLightDefaultAccessor
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this

                    if (isSetter) {
                        val type =
                            baseResolveProviderService.getType(
                                original,
                                this@UastFakeSourceLightDefaultAccessor,
                                isForFake = true
                            ) ?: UastErrorType
                        val nullability = baseResolveProviderService.nullability(original)
                        this.addParameter(
                            UastKotlinPsiSetterParameter(type, parameterList, original, nullability)
                        )
                    }
                }
            }
        }

    override fun getParameterList(): PsiParameterList = _parameterList
}