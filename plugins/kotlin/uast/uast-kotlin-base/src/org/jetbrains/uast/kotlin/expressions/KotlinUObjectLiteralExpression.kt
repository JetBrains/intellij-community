// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUObjectLiteralExpression(
    override val sourcePsi: KtObjectLiteralExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UObjectLiteralExpression, UCallExpression, DelegatedMultiResolve, KotlinUElementWithType {

    private val declarationPart = UastLazyPart<UClass>()
    private val classReferencePart = UastLazyPart<UReferenceExpression?>()
    private val typeArgumentsPart = UastLazyPart<List<PsiType>>()
    private val superClassConstructorCallPart = UastLazyPart<KtSuperTypeCallEntry?>()
    private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

    override val declaration: UClass
        get() = declarationPart.getOrBuild {
            sourcePsi.objectDeclaration.toLightClass()
                ?.let { languagePlugin?.convertOpt(it, this) }
                ?: KotlinInvalidUClass("<invalid object code>", sourcePsi, this)
        }

    override fun getExpressionType() =
        sourcePsi.objectDeclaration.toPsiType()

    private val superClassConstructorCall: KtSuperTypeCallEntry?
        get() = superClassConstructorCallPart.getOrBuild {
            sourcePsi.objectDeclaration.superTypeListEntries.firstOrNull { it is KtSuperTypeCallEntry } as? KtSuperTypeCallEntry
        }

    override val classReference: UReferenceExpression?
        get() = classReferencePart.getOrBuild {
            superClassConstructorCall?.let { ObjectLiteralClassReference(it, this) }
        }

    override val valueArgumentCount: Int
        get() = superClassConstructorCall?.valueArguments?.size ?: 0

    override val valueArguments: List<UExpression>
        get() = valueArgumentsPart.getOrBuild {
            val psi = superClassConstructorCall ?: return@getOrBuild emptyList<UExpression>()
            psi.valueArguments.map { baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it.getArgumentExpression(), this) }
        }

    override val typeArgumentCount: Int
        get() = superClassConstructorCall?.typeArguments?.size ?: 0

    override val typeArguments: List<PsiType>
        get() = typeArgumentsPart.getOrBuild {
            val psi = superClassConstructorCall ?: return@getOrBuild emptyList<PsiType>()
            psi.typeArguments.map { typeArgument ->
                typeArgument.typeReference?.let { baseResolveProviderService.resolveToType(it, this, isBoxed = true) } ?: UastErrorType
            }
        }

    override fun resolve() =
        superClassConstructorCall?.let { baseResolveProviderService.resolveCall(it) }

    override fun getArgumentForParameter(i: Int): UExpression? =
        superClassConstructorCall?.let {
            baseResolveProviderService.getArgumentForParameter(it, i, this)
        }

    /**
     * `super` call in the fake-constructor of anonymous class
     */
    val constructorCall: UExpression?
        get() = (this.declaration.methods.asSequence().filterIsInstance<KotlinConstructorUMethod>()
            .singleOrNull()?.uastBody as? KotlinLazyUBlockExpression)
            ?.expressions
            ?.firstOrNull()

    private class ObjectLiteralClassReference(
        override val sourcePsi: KtSuperTypeCallEntry,
        givenParent: UElement?
    ) : KotlinAbstractUElement(givenParent), USimpleNameReferenceExpression {

        override val javaPsi: PsiElement?
            get() = null

        override val psi: KtSuperTypeCallEntry
            get() = sourcePsi

        override fun resolve() =
            baseResolveProviderService.resolveToClassIfConstructorCall(sourcePsi, this)
                ?: baseResolveProviderService.resolveCall(sourcePsi)?.containingClass

        override val uAnnotations: List<UAnnotation>
            get() = emptyList()

        override val resolvedName: String
            get() = identifier

        override val identifier: String
            get() = psi.name ?: referenceNameElement.sourcePsi?.text ?: "<error>"

        override val referenceNameElement: UElement
            get() = KotlinUIdentifier(psi.typeReference?.nameElement, this)
    }

}
