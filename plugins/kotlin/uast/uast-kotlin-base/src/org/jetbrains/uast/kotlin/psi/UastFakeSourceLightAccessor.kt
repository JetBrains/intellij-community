// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

internal class UastFakeSourceLightAccessor(
    original: KtPropertyAccessor,
    containingClass: PsiClass,
) : UastFakeSourceLightAccessorBase<KtPropertyAccessor>(
    original.property,
    original,
    containingClass,
    original.isSetter
) {
    private val parameterListPart = UastLazyPart<PsiParameterList>()

    private val _parameterList: PsiParameterList
        get() = parameterListPart.getOrBuild {
            object : LightParameterListBuilder(original.manager, original.language) {
                override fun getParent(): PsiElement = this@UastFakeSourceLightAccessor
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this

                    for ((i, p) in original.valueParameters.withIndex()) {
                        val type = baseResolveProviderService.getType(p, this@UastFakeSourceLightAccessor, isForFake = true)
                            ?: UastErrorType
                        val adjustedType = if (p.isVarArg && type is PsiArrayType)
                            PsiEllipsisType(type.componentType, type.annotationProvider)
                        else type
                        this.addParameter(
                            UastKotlinPsiParameter(
                                baseResolveProviderService,
                                p.name ?: "p$i",
                                adjustedType,
                                parameterList,
                                original.language,
                                p.isVarArg,
                                p.defaultValue,
                                p
                            )
                        )
                    }
                }
            }
        }

    override fun getParameterList(): PsiParameterList = _parameterList
}
