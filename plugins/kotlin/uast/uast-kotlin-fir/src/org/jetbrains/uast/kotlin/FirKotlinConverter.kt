// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toPsiParameters
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.expressions.FirKotlinUArrayAccessExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUBinaryExpression
import org.jetbrains.uast.kotlin.expressions.FirKotlinUSimpleReferenceExpression
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

internal object FirKotlinConverter : BaseKotlinConverter {
    override fun convertAnnotation(annotationEntry: KtAnnotationEntry, givenParent: UElement?): UAnnotation {
        // TODO: need to polish/implement annotations more
        return FirKotlinUAnnotation(annotationEntry, givenParent)
    }

    internal fun convertDeclarationOrElement(
        element: PsiElement,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement? {
        if (element is UElement) return element

        // TODO: cache inside PsiElement?

        return convertDeclaration(element, givenParent, requiredTypes)
            ?: convertPsiElement(element, givenParent, requiredTypes)
    }

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

        return with(requiredTypes) {
            when (original) {
                is KtFile -> {
                    convertKtFile(original, givenParent, requiredTypes).firstOrNull()
                }
                is FakeFileForLightClass -> {
                    el<UFile> { KotlinUFile(original.navigationElement, firKotlinUastPlugin) }
                }

                is KtLightClass -> {
                    // TODO: differentiate enum entry
                    el<UClass> { FirKotlinUClass.create(original, givenParent) }
                }
                is KtClassOrObject -> {
                    convertClassOrObject(original, givenParent, requiredTypes).firstOrNull()
                }
                // TODO: KtEnumEntry

                is KtLightField -> {
                    // TODO: differentiate enum constant
                    el<UField>(buildKtOpt(original.kotlinOrigin, ::KotlinUField))
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
                    el<UMethod> { FirKotlinUMethod.create(original, givenParent) }
                }

                is KtLightMethod -> {
                    // .Companion is needed because of KT-13934
                    el<UMethod>(build(FirKotlinUMethod.Companion::create))
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
                        el<UMethod> { FirKotlinUMethod.create(original, givenParent) }
                    }
                }

                // TODO: KtAnnotationEntry
                // TODO: KtCallExpression (for nested annotation)

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
          alternative { element.findFacadeClass()?.let { FirKotlinUClass.create(it, givenParent) } }
        )
    }

    internal fun convertClassOrObject(
        element: KtClassOrObject,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        val ktLightClass = element.toLightClass() ?: return emptySequence()
        val uClass = FirKotlinUClass.create(ktLightClass, givenParent)
        return requiredTypes.accommodate(
            // Class
            alternative { uClass },
            // Object
            alternative primaryConstructor@{
                val primaryConstructor = element.primaryConstructor ?: return@primaryConstructor null
                uClass.methods.asSequence()
                    .filter { it.sourcePsi == primaryConstructor }
                    .firstOrNull()
            }
        )
    }

    private fun convertToPropertyAlternatives(
        methods: LightClassUtil.PropertyAccessorsPsiMethods?,
        givenParent: UElement?
    ): Array<UElementAlternative<*>> =
        if (methods != null)
            arrayOf(
                alternative { methods.backingField?.let { KotlinUField(it, getKotlinMemberOrigin(it), givenParent) } },
                alternative { methods.getter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } },
                alternative { methods.setter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } }
            )
        else emptyArray()

    internal fun convertNonLocalProperty(
        property: KtProperty,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> =
        requiredTypes.accommodate(*convertToPropertyAlternatives(LightClassUtil.getLightClassPropertyMethods(property), givenParent))

    internal fun convertParameter(
        element: KtParameter,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> =
        requiredTypes.accommodate(
            alternative uParam@{
                val lightParameter = element.toPsiParameters().find { it.name == element.name } ?: return@uParam null
                KotlinUParameter(lightParameter, element, givenParent)
            },
            alternative catch@{
                val uCatchClause = element.parent?.parent?.safeAs<KtCatchClause>()?.toUElementOfType<UCatchClause>() ?: return@catch null
                uCatchClause.parameters.firstOrNull { it.sourcePsi == element }
            },
            *convertToPropertyAlternatives(LightClassUtil.getLightClassPropertyMethods(element), givenParent)
        )

    override fun convertPsiElement(
        element: PsiElement?,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement? {
        if (element == null) return null

        fun <P : PsiElement> build(ctor: (P, UElement?) -> UElement): () -> UElement? = {
            @Suppress("UNCHECKED_CAST")
            ctor(element as P, givenParent)
        }

        return with(requiredTypes) {
            when (element) {
                is KtParameterList -> {
                    el<UDeclarationsExpression> {
                        val declarationsExpression = KotlinUDeclarationsExpression(null, givenParent, null)
                        declarationsExpression.apply {
                            declarations = element.parameters.mapIndexed { i, p ->
                                KotlinUParameter(UastKotlinPsiParameter.create(p, element, declarationsExpression, i), p, this)
                            }
                        }
                    }
                }
                is KtClassBody -> {
                    el<UExpressionList>(build(KotlinUExpressionList.Companion::createClassBody))
                }
                is KtCatchClause -> {
                    el<UCatchClause>(build(::KotlinUCatchClause))
                }
                is KtVariableDeclaration -> {
                    if (element is KtProperty && !element.isLocal) {
                        convertNonLocalProperty(element, givenParent, this).firstOrNull()
                    } else {
                        el<UVariable> { convertVariablesDeclaration(element, givenParent).declarations.singleOrNull() }
                            ?: expr<UDeclarationsExpression> { convertExpression(element, givenParent, requiredTypes) }
                    }
                }
                is KtExpression -> {
                    convertExpression(element, givenParent, requiredTypes)
                }
                is KtLiteralStringTemplateEntry, is KtEscapeStringTemplateEntry -> {
                    el<ULiteralExpression>(build(::KotlinStringULiteralExpression))
                }
                is KtStringTemplateEntry -> {
                    element.expression?.let { convertExpression(it, givenParent, requiredTypes) }
                        ?: expr<UExpression> { UastEmptyExpression(givenParent) }
                }
                is KtWhenEntry -> el<USwitchClauseExpressionWithBody>(build(::KotlinUSwitchEntry))
                is KtWhenCondition -> convertWhenCondition(element, givenParent, requiredTypes)
                is KtTypeReference ->
                    requiredTypes.accommodate(
                        alternative { KotlinUTypeReferenceExpression(element, givenParent) },
                        alternative { convertReceiverParameter(element) }
                    ).firstOrNull()
                is KtConstructorDelegationCall ->
                    el<UCallExpression> { KotlinUFunctionCallExpression(element, givenParent) }
                is KtSuperTypeCallEntry ->
                    el<UExpression> {
                        (element.getParentOfType<KtClassOrObject>(true)?.parent as? KtObjectLiteralExpression)
                            ?.toUElementOfType<UExpression>()
                            ?: KotlinUFunctionCallExpression(element, givenParent)
                    }
                is KtImportDirective -> {
                    el<UImportStatement>(build(::KotlinUImportStatement))
                }
                is PsiComment -> el<UComment>(build(::UComment))
                is KDocName -> {
                    if (element.getQualifier() == null)
                        el<USimpleNameReferenceExpression> {
                            element.lastChild?.let { psiIdentifier ->
                                KotlinStringUSimpleReferenceExpression(psiIdentifier.text, givenParent, element, element)
                            }
                        }
                    else el<UQualifiedReferenceExpression>(build(::KotlinDocUQualifiedReferenceExpression))
                }
                is LeafPsiElement -> {
                    when {
                        element.elementType in identifiersTokens -> {
                            if (element.elementType != KtTokens.OBJECT_KEYWORD ||
                                element.getParentOfType<KtObjectDeclaration>(false)?.nameIdentifier == null
                            )
                                el<UIdentifier>(build(::KotlinUIdentifier))
                            else null
                        }
                        element.elementType in KtTokens.OPERATIONS && element.parent is KtOperationReferenceExpression -> {
                            el<UIdentifier>(build(::KotlinUIdentifier))
                        }
                        element.elementType == KtTokens.LBRACKET && element.parent is KtCollectionLiteralExpression -> {
                            el<UIdentifier> {
                                UIdentifier(
                                    element,
                                    KotlinUCollectionLiteralExpression(element.parent as KtCollectionLiteralExpression, null)
                                )
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    // TODO: forceUInjectionHost (for test)?

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
                        requiredTypes.contains(UInjectionHost::class.java) -> {
                            expr<UInjectionHost> { KotlinStringTemplateUPolyadicExpression(expression, givenParent) }
                        }
                        expression.entries.isEmpty() -> {
                            expr<ULiteralExpression> { KotlinStringULiteralExpression(expression, givenParent, "") }
                        }
                        expression.entries.size == 1 -> {
                            convertEntry(expression.entries[0], givenParent, requiredTypes)
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
                            declarations = listOf(FirKotlinUClass.create(lightClass, this))
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

                else -> expr<UExpression>(build(::UnknownKotlinExpression))
            }
        }
    }
}
