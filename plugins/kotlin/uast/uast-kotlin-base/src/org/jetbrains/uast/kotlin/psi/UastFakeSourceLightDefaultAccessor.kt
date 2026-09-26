// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParameterList
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.SmartSet
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
                        val nullability = baseResolveProviderService.nullability(original)
                        addParameter(UastKotlinPsiSetterParameter(parameterList, original, nullability))
                    }
                }
            }
        }

    override fun getParameterList(): PsiParameterList = _parameterList

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        // Annotations on property, along with use-site target
        annotationFromProperty(annotations)
    }
}