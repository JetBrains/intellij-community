// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

class KotlinUEnumConstant(
    psi: PsiEnumConstant,
    override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UEnumConstantExPlaceHolder, UCallExpressionEx, DelegatedMultiResolve, PsiEnumConstant by psi {

    override val initializingClass: UClass? by lz {
        (psi.initializingClass as? KtLightClass)?.let { initializingClass ->
            KotlinUClass.create(initializingClass, this)
        }
    }

    override fun getInitializer(): PsiExpression? = super<AbstractKotlinUVariable>.getInitializer()

    override fun getOriginalElement(): PsiElement? = super<AbstractKotlinUVariable>.getOriginalElement()

    override val javaPsi = unwrap<UEnumConstant, PsiEnumConstant>(psi)

    override val psi = javaPsi

    override fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean = true

    override fun getContainingFile(): PsiFile {
        return super.getContainingFile()
    }

    override fun getNameIdentifier(): PsiIdentifier {
        return super.getNameIdentifier()
    }

    override val kind: UastCallKind
        get() = UastCallKind.CONSTRUCTOR_CALL

    override val receiver: UExpression?
        get() = null

    override val receiverType: PsiType?
        get() = null

    override val methodIdentifier: UIdentifier?
        get() = null

    override val classReference: UReferenceExpression?
        get() = KotlinEnumConstantClassReference(psi, sourcePsi, this)

    override val typeArgumentCount: Int
        get() = 0

    override val typeArguments: List<PsiType>
        get() = emptyList()

    override val valueArgumentCount: Int
        get() = psi.argumentList?.expressions?.size ?: 0

    override val valueArguments by lz(fun(): List<UExpression> {
        val ktEnumEntry = sourcePsi as? KtEnumEntry ?: return emptyList()
        val ktSuperTypeCallEntry = ktEnumEntry.initializerList?.initializers?.firstOrNull() as? KtSuperTypeCallEntry ?: return emptyList()
        return ktSuperTypeCallEntry.valueArguments.map {
            it.getArgumentExpression()?.let { getLanguagePlugin().convertElement(it, this) } as? UExpression ?: UastEmptyExpression(this)
        }
    })

    override val returnType: PsiType?
        get() = uastParent?.getAsJavaPsiElement(PsiClass::class.java)?.let { PsiTypesUtil.getClassType(it) }

    override fun resolve() = psi.resolveMethod()

    override val methodName: String?
        get() = null

    private class KotlinEnumConstantClassReference(
        override val psi: PsiEnumConstant,
        override val sourcePsi: KtElement?,
        givenParent: UElement?
    ) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression {
        override val javaPsi: PsiElement?
            get() = psi

        override fun resolve() = psi.containingClass
        override val resolvedName: String?
            get() = psi.containingClass?.name
        override val identifier: String
            get() = psi.containingClass?.name ?: "<error>"
    }

    override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

}
