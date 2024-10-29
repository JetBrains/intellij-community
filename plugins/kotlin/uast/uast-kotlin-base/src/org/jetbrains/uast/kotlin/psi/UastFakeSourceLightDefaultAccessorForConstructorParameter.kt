// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

internal class UastFakeSourceLightDefaultAccessorForConstructorParameter(
    original: KtParameter,
    containingClass: PsiClass,
    private val isSetter: Boolean,
) : UastFakeSourceLightMethodBase<KtParameter>(original, containingClass) {

    private val parameterListPart = UastLazyPart<PsiParameterList>()

    private val _parameterList: PsiParameterList
        get() = parameterListPart.getOrBuild {
            object : LightParameterListBuilder(original.manager, original.language) {
                override fun getParent(): PsiElement = this@UastFakeSourceLightDefaultAccessorForConstructorParameter
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this

                    if (isSetter) {
                        val nullability = baseResolveProviderService.nullability(original)
                        addParameter(UastKotlinPsiSetterParameter(parameterList, original, nullability))
                    }
                }
            }
        }

    override fun getParameterList(): PsiParameterList = _parameterList

    override fun getName(): String {
        val propertyName = original.name ?: ""
        // TODO: what about @JvmName w/ use-site target?
        return if (isSetter) JvmAbi.setterName(propertyName) else JvmAbi.getterName(propertyName)
    }

    override fun getReturnType(): PsiType? {
        if (isSetter) {
            return PsiTypes.voidType()
        }

        return super.getReturnType()
    }

    override fun hasModifierProperty(name: String): Boolean {
        if (name == PsiModifier.FINAL) return true
        return super.hasModifierProperty(name)
    }
}