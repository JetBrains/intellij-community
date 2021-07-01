// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParameterEx

open class KotlinUParameter(
    psi: PsiParameter,
    final override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UParameterEx, PsiParameter by psi {

    final override val javaPsi = unwrap<UParameter, PsiParameter>(psi)

    override val psi = javaPsi

    private val isLightConstructorParam by lz { psi.getParentOfType<PsiMethod>(true)?.isConstructor }

    private val isKtConstructorParam by lz { sourcePsi?.getParentOfType<KtCallableDeclaration>(true)?.let { it is KtConstructor<*> } }

    override fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean {
        if (sourcePsi !is KtParameter) return false
        if (isKtConstructorParam == isLightConstructorParam && target == null) return true
        if (sourcePsi.parent.parent is KtCatchClause && target == null) return true
        when (target) {
            AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER -> return isLightConstructorParam == true
            AnnotationUseSiteTarget.SETTER_PARAMETER -> return isLightConstructorParam != true
            else -> return false
        }
    }

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
