// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParameterList
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.utils.SmartSet
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
                        addParameter(
                            UastKotlinPsiParameter(
                                p.name ?: "p$i",
                                parameterList,
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

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        // Annotations on property accessor ([original] of [KtPropertyAccessor])
        super.computeAnnotations(annotations)
        // Annotations on property, along with use-site target
        annotationFromProperty(annotations)
    }
}
