// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.*

@ApiStatus.Internal
open class KotlinUParameter(
    psi: PsiParameter,
    final override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UParameterEx, PsiParameter by psi {

    private val isLightConstructorParamLazy = UastLazyPart<Boolean?>()
    private val isKtConstructorParamLazy = UastLazyPart<Boolean?>()

    final override val javaPsi = unwrap<UParameter, PsiParameter>(psi)

    override val psi = javaPsi

    private val isLightConstructorParam: Boolean?
        get() = isLightConstructorParamLazy.getOrBuild { psi.getParentOfType<PsiMethod>(true)?.isConstructor }

    private val isKtConstructorParam: Boolean?
        get() = isKtConstructorParamLazy.getOrBuild {
            sourcePsi?.getParentOfType<KtCallableDeclaration>(true)?.let { it is KtConstructor<*> }
        }

    override fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean {
        if (sourcePsi !is KtParameter) return false
        if (isKtConstructorParam == isLightConstructorParam && target == null) return true
        if ((sourcePsi.parent as? KtParameterList)?.parent is KtCatchClause && target == null) return true
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
