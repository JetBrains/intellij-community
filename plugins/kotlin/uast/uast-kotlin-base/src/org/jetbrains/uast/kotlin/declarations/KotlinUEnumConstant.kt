// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UEnumConstantEx
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getAsJavaPsiElement
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUEnumConstant(
    psi: PsiEnumConstant,
    override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UEnumConstantEx, UCallExpression, DelegatedMultiResolve, PsiEnumConstant by psi {

    private val initializingClassPart = UastLazyPart<UClass?>()
    private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

    override val initializingClass: UClass?
        get() = initializingClassPart.getOrBuild {
            (psi.initializingClass as? KtLightClass)?.let { initializingClass ->
                KotlinUClass.create(initializingClass, this)
            }
        }

    override fun getInitializer(): PsiExpression? = super<AbstractKotlinUVariable>.getInitializer()

    override fun getOriginalElement(): PsiElement? = super<AbstractKotlinUVariable>.getOriginalElement()

    override val javaPsi: PsiEnumConstant = unwrap<UEnumConstant, PsiEnumConstant>(psi)

    override val psi: PsiEnumConstant = javaPsi

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

    override val classReference: UReferenceExpression
        get() = KotlinEnumConstantClassReference(psi, sourcePsi, this)

    override val typeArgumentCount: Int
        get() = 0

    override val typeArguments: List<PsiType>
        get() = emptyList()

    override val valueArgumentCount: Int
        get() = psi.argumentList?.expressionCount ?: 0

    override val valueArguments: List<UExpression>
        get() = valueArgumentsPart.getOrBuild {
            val ktEnumEntry = sourcePsi as? KtEnumEntry ?: return@getOrBuild emptyList()
            val ktSuperTypeCallEntry =
                ktEnumEntry.initializerList?.initializers?.firstOrNull() as? KtSuperTypeCallEntry ?: return@getOrBuild emptyList()
            ktSuperTypeCallEntry.valueArguments.map { valueArgument ->
                valueArgument.getArgumentExpression()?.let { languagePlugin?.convertElement(it, this) } as? UExpression
                    ?: UastEmptyExpression(this)
            }
        }

    override val returnType: PsiType?
        get() = uastParent?.getAsJavaPsiElement(PsiClass::class.java)?.let { PsiTypesUtil.getClassType(it) }

    override fun resolve(): PsiMethod? = psi.resolveMethod()

    override val methodName: String?
        get() = null

    private class KotlinEnumConstantClassReference(
        override val psi: PsiEnumConstant,
        override val sourcePsi: KtElement?,
        givenParent: UElement?
    ) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression {
        override val javaPsi: PsiElement
            get() = psi

        override fun resolve() = psi.containingClass
        override val resolvedName: String?
            get() = psi.containingClass?.name
        override val identifier: String
            get() = psi.containingClass?.name ?: "<error>"
    }

    override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

}
