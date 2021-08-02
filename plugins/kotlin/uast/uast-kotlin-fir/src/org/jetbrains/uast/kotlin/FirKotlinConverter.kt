// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.expressions.FirKotlinUArrayAccessExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUBinaryExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUSimpleReferenceExpression
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin
import org.jetbrains.uast.kotlin.psi.*

internal object FirKotlinConverter : BaseKotlinConverter {

    override fun convertDeclaration(
        element: PsiElement,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement? {
        val original = element.originalElement

        fun <P : PsiElement> build(ctor: (P, UElement?) -> UElement): () -> UElement? = {
            @Suppress("UNCHECKED_CAST")
            ctor(original as P, givenParent)
        }

        fun <P : PsiElement, K : KtElement> buildKt(ktElement: K, ctor: (P, K, UElement?) -> UElement): () -> UElement? = {
            @Suppress("UNCHECKED_CAST")
            ctor(original as P, ktElement, givenParent)
        }

        fun <P : PsiElement, K : KtElement> buildKtOpt(ktElement: K?, ctor: (P, K?, UElement?) -> UElement): () -> UElement? = {
            @Suppress("UNCHECKED_CAST")
            ctor(original as P, ktElement, givenParent)
        }

        fun Array<out Class<out UElement>>.convertToUField(original: PsiField, kotlinOrigin: KtElement?): UElement? =
            if (original is PsiEnumConstant)
                el<UEnumConstant>(buildKtOpt(kotlinOrigin, ::KotlinUEnumConstant))
            else
                el<UField>(buildKtOpt(kotlinOrigin, ::KotlinUField))

        return with(requiredTypes) {
            when (original) {
                is KtFile -> {
                    convertKtFile(original, givenParent, requiredTypes).firstOrNull()
                }
                is FakeFileForLightClass -> {
                    el<UFile> { KotlinUFile(original.navigationElement, firKotlinUastPlugin) }
                }

                is KtLightClass -> {
                    when (original.kotlinOrigin) {
                        is KtEnumEntry -> el<UEnumConstant> {
                            convertEnumEntry(original.kotlinOrigin as KtEnumEntry, givenParent)
                        }
                        else -> el<UClass> { KotlinUClass.create(original, givenParent) }
                    }
                }
                is KtClassOrObject -> {
                    convertClassOrObject(original, givenParent, requiredTypes).firstOrNull()
                }
                is KtEnumEntry -> {
                    el<UEnumConstant> {
                        convertEnumEntry(original, givenParent)
                    }
                }

                is KtLightField -> {
                    convertToUField(original, original.kotlinOrigin)
                }
                is KtLightFieldForSourceDeclarationSupport -> {
                    // KtLightFieldForDecompiledDeclaration is not a KtLightField
                    convertToUField(original, original.kotlinOrigin)
                }
                is UastKotlinPsiVariable -> {
                    el<ULocalVariable>(buildKt(original.ktElement, ::KotlinULocalVariable))
                }
                is KtLightParameter -> {
                    el<UParameter>(buildKtOpt(original.kotlinOrigin, ::KotlinUParameter))
                }
                is KtParameter -> {
                    convertParameter(original, givenParent, requiredTypes).firstOrNull()
                }
                is UastKotlinPsiParameter -> {
                    el<UParameter>(buildKt(original.ktParameter, ::KotlinUParameter))
                }
                is UastKotlinPsiParameterBase<*> -> el<UParameter> {
                    original.ktOrigin.safeAs<KtTypeReference>()?.let { convertReceiverParameter(it) }
                }
                is KtProperty -> {
                    if (original.isLocal) {
                        convertPsiElement(original, givenParent, requiredTypes)
                    } else {
                        convertNonLocalProperty(original, givenParent, requiredTypes).firstOrNull()
                    }
                }
                is KtPropertyAccessor -> {
                    el<UMethod> {
                        val lightMethod = LightClassUtil.getLightClassAccessorMethod(original) ?: return null
                        convertDeclaration(lightMethod, givenParent, requiredTypes)
                    }
                }

                is KtLightMethod -> {
                    // .Companion is needed because of KT-13934
                    el<UMethod>(build(KotlinUMethod.Companion::create))
                }
                is UastFakeLightMethod -> {
                    el<UMethod> {
                        val ktFunction = original.original
                        if (ktFunction.isLocal)
                            convertDeclaration(ktFunction, givenParent, requiredTypes)
                        else
                            KotlinUMethodWithFakeLightDelegate(ktFunction, original, givenParent)
                    }
                }
                is UastFakeLightPrimaryConstructor -> {
                    convertFakeLightConstructorAlternatives(original, givenParent, requiredTypes).firstOrNull()
                }
                is KtFunction -> {
                    if (original.isLocal) {
                        el<ULambdaExpression> {
                            val parent = original.parent
                            when {
                                parent is KtLambdaExpression -> {
                                    KotlinULambdaExpression(parent, givenParent) // your parent is the ULambdaExpression
                                }
                                original.name.isNullOrEmpty() -> {
                                    createLocalFunctionLambdaExpression(original, givenParent)
                                }
                                else -> {
                                    val uDeclarationsExpression = createLocalFunctionDeclaration(original, givenParent)
                                    val localFunctionVar = uDeclarationsExpression.declarations.single() as KotlinLocalFunctionUVariable
                                    localFunctionVar.uastInitializer
                                }
                            }
                        }
                    } else {
                        el<UMethod> {
                            val lightMethod = LightClassUtil.getLightClassMethod(original)
                            if (lightMethod != null)
                                convertDeclaration(lightMethod, givenParent, requiredTypes)
                            else {
                                val ktLightClass = getLightClassForFakeMethod(original) ?: return null
                                KotlinUMethodWithFakeLightDelegate(original, ktLightClass, givenParent)
                            }
                        }
                    }
                }

                is KtAnnotationEntry -> el<UAnnotation>(build(::convertAnnotation))
                is KtCallExpression ->
                    if (requiredTypes.isAssignableFrom(KotlinUNestedAnnotation::class.java) &&
                        !requiredTypes.isAssignableFrom(UCallExpression::class.java)
                    ) {
                        el<UAnnotation> { KotlinUNestedAnnotation.create(original, givenParent) }
                    } else null
                is KtLightAnnotationForSourceEntry -> convertDeclarationOrElement(original.kotlinOrigin, givenParent, requiredTypes)

                is KtDelegatedSuperTypeEntry -> el<KotlinSupertypeDelegationUExpression> {
                    KotlinSupertypeDelegationUExpression(original, givenParent)
                }

                else -> null
            }
        }
    }

    internal fun convertKtFile(
        element: KtFile,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        return requiredTypes.accommodate(
            // File
          alternative { KotlinUFile(element, firKotlinUastPlugin) },
            // Facade
          alternative { element.findFacadeClass()?.let { KotlinUClass.create(it, givenParent) } }
        )
    }

    internal fun convertParameter(
        element: KtParameter,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> =
        requiredTypes.accommodate(
            alternative uParam@{
                val lightMethod = when (val ownerFunction = element.ownerFunction) {
                    is KtFunction -> LightClassUtil.getLightClassMethod(ownerFunction)
                        ?: getLightClassForFakeMethod(ownerFunction)
                            ?.takeIf { !it.isAnnotationType }
                            ?.let { UastFakeLightMethod(ownerFunction, it) }
                    is KtPropertyAccessor -> LightClassUtil.getLightClassAccessorMethod(ownerFunction)
                    else -> null
                } ?: return@uParam null
                val lightParameter = lightMethod.parameterList.parameters.find { it.name == element.name } ?: return@uParam null
                KotlinUParameter(lightParameter, element, givenParent)
            },
            alternative catch@{
                val uCatchClause = element.parent?.parent?.safeAs<KtCatchClause>()?.toUElementOfType<UCatchClause>() ?: return@catch null
                uCatchClause.parameters.firstOrNull { it.sourcePsi == element }
            },
            *convertToPropertyAlternatives(LightClassUtil.getLightClassPropertyMethods(element), givenParent)
        )

    var forceUInjectionHost = Registry.`is`("kotlin.fir.uast.force.uinjectionhost", false)
        @TestOnly
        set(value) {
            field = value
        }

    override fun forceUInjectionHost(): Boolean {
        return forceUInjectionHost
    }

    override fun convertExpression(
        expression: KtExpression,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression? {

        fun <P : PsiElement> build(ctor: (P, UElement?) -> UExpression): () -> UExpression? {
            return {
                @Suppress("UNCHECKED_CAST")
                ctor(expression as P, givenParent)
            }
        }

        return with(requiredTypes) {
            when (expression) {
                is KtVariableDeclaration -> expr<UDeclarationsExpression>(build(::convertVariablesDeclaration))
                is KtDestructuringDeclaration -> expr<UDeclarationsExpression> {
                    val declarationsExpression = KotlinUDestructuringDeclarationExpression(givenParent, expression)
                    declarationsExpression.apply {
                        val tempAssignment = KotlinULocalVariable(
                            UastKotlinPsiVariable.create(expression, declarationsExpression),
                            expression,
                            declarationsExpression
                        )
                        // TODO: fill declarations with assignments
                        declarations = listOf(tempAssignment)
                    }
                }

                is KtStringTemplateExpression -> {
                    when {
                        forceUInjectionHost || requiredTypes.contains(UInjectionHost::class.java) -> {
                            expr<UInjectionHost> { KotlinStringTemplateUPolyadicExpression(expression, givenParent) }
                        }
                        expression.entries.isEmpty() -> {
                            expr<ULiteralExpression> { KotlinStringULiteralExpression(expression, givenParent, "") }
                        }
                        expression.entries.size == 1 -> {
                            convertStringTemplateEntry(expression.entries[0], givenParent, requiredTypes)
                        }
                        else -> {
                            expr<KotlinStringTemplateUPolyadicExpression> {
                                KotlinStringTemplateUPolyadicExpression(expression, givenParent)
                            }
                        }
                    }
                }
                is KtCollectionLiteralExpression -> expr<UCallExpression>(build(::KotlinUCollectionLiteralExpression))
                is KtConstantExpression -> expr<ULiteralExpression>(build(::KotlinULiteralExpression))

                is KtLabeledExpression -> expr<ULabeledExpression>(build(::KotlinULabeledExpression))
                is KtParenthesizedExpression -> expr<UParenthesizedExpression>(build(::KotlinUParenthesizedExpression))

                is KtBlockExpression -> expr<UBlockExpression> {
                    if (expression.parent is KtFunctionLiteral &&
                        expression.parent.parent is KtLambdaExpression &&
                        givenParent !is KotlinULambdaExpression
                    ) {
                        KotlinULambdaExpression(expression.parent.parent as KtLambdaExpression, givenParent).body
                    } else
                        KotlinUBlockExpression(expression, givenParent)
                }
                is KtReturnExpression -> expr<UReturnExpression>(build(::KotlinUReturnExpression))
                is KtThrowExpression -> expr<UThrowExpression>(build(::KotlinUThrowExpression))
                is KtTryExpression -> expr<UTryExpression>(build(::KotlinUTryExpression))

                is KtBreakExpression -> expr<UBreakExpression>(build(::KotlinUBreakExpression))
                is KtContinueExpression -> expr<UContinueExpression>(build(::KotlinUContinueExpression))
                is KtDoWhileExpression -> expr<UDoWhileExpression>(build(::KotlinUDoWhileExpression))
                is KtWhileExpression -> expr<UWhileExpression>(build(::KotlinUWhileExpression))
                is KtForExpression -> expr<UForEachExpression>(build(::KotlinUForEachExpression))

                is KtWhenExpression -> expr<USwitchExpression>(build(::KotlinUSwitchExpression))
                is KtIfExpression -> expr<UIfExpression>(build(::KotlinUIfExpression))

                is KtBinaryExpressionWithTypeRHS -> expr<UBinaryExpressionWithType>(build(::KotlinUBinaryExpressionWithType))
                is KtIsExpression -> expr<UBinaryExpressionWithType>(build(::KotlinUTypeCheckExpression))

                is KtArrayAccessExpression -> expr<UArrayAccessExpression>(build(::FirKotlinUArrayAccessExpression))

                is KtThisExpression -> expr<UThisExpression>(build(::KotlinUThisExpression))
                is KtSuperExpression -> expr<USuperExpression>(build(::KotlinUSuperExpression))
                is KtCallableReferenceExpression -> expr<UCallableReferenceExpression>(build(::KotlinUCallableReferenceExpression))
                is KtClassLiteralExpression -> expr<UClassLiteralExpression>(build(::KotlinUClassLiteralExpression))
                is KtObjectLiteralExpression -> expr<UObjectLiteralExpression>(build(::KotlinUObjectLiteralExpression))
                is KtDotQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUQualifiedReferenceExpression))
                is KtSafeQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUSafeQualifiedExpression))
                is KtSimpleNameExpression -> expr<USimpleNameReferenceExpression>(build(::FirKotlinUSimpleReferenceExpression))
                is KtCallExpression -> expr<UCallExpression>(build(::KotlinUFunctionCallExpression))

                is KtBinaryExpression -> {
                    if (expression.operationToken == KtTokens.ELVIS) {
                        expr<UExpressionList>(build(::createElvisExpression))
                    } else {
                        expr<UBinaryExpression>(build(::FirKotlinUBinaryExpression))
                    }
                }
                is KtPrefixExpression -> expr<UPrefixExpression>(build(::KotlinUPrefixExpression))
                is KtPostfixExpression -> expr<UPostfixExpression>(build(::KotlinUPostfixExpression))

                is KtClassOrObject -> expr<UDeclarationsExpression> {
                    expression.toLightClass()?.let { lightClass ->
                        KotlinUDeclarationsExpression(givenParent).apply {
                            declarations = listOf(KotlinUClass.create(lightClass, this))
                        }
                    } ?: UastEmptyExpression(givenParent)
                }
                is KtLambdaExpression -> expr<ULambdaExpression>(build(::KotlinULambdaExpression))
                is KtFunction -> {
                    if (expression.name.isNullOrEmpty()) {
                        expr<ULambdaExpression>(build(::createLocalFunctionLambdaExpression))
                    } else {
                        expr<UDeclarationsExpression>(build(::createLocalFunctionDeclaration))
                    }
                }
                is KtAnnotatedExpression -> {
                    expression.baseExpression
                        ?.let { convertExpression(it, givenParent, requiredTypes) }
                        ?: expr<UExpression>(build(::UnknownKotlinExpression))
                }

                else -> expr<UExpression>(build(::UnknownKotlinExpression))
            }
        }
    }
}
