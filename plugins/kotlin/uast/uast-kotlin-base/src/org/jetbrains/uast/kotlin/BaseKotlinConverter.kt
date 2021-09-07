// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.UElementAlternative
import org.jetbrains.uast.internal.accommodate
import org.jetbrains.uast.internal.alternative
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.psi.*

interface BaseKotlinConverter {
    val languagePlugin: UastLanguagePlugin

    fun unwrapElements(element: PsiElement?): PsiElement? = when (element) {
        is KtValueArgumentList -> unwrapElements(element.parent)
        is KtValueArgument -> unwrapElements(element.parent)
        is KtDeclarationModifierList -> unwrapElements(element.parent)
        is KtContainerNode -> unwrapElements(element.parent)
        is KtSimpleNameStringTemplateEntry -> unwrapElements(element.parent)
        is PsiParameterList -> unwrapElements(element.parent)
        is KtTypeElement -> unwrapElements(element.parent)
        is KtSuperTypeList -> unwrapElements(element.parent)
        is KtFinallySection -> unwrapElements(element.parent)
        is KtAnnotatedExpression -> unwrapElements(element.parent)
        is KtWhenConditionWithExpression -> unwrapElements(element.parent)
        is KDocLink -> unwrapElements(element.parent)
        is KDocSection -> unwrapElements(element.parent)
        is KDocTag -> unwrapElements(element.parent)
        else -> element
    }

    fun convertAnnotation(
        annotationEntry: KtAnnotationEntry,
        givenParent: UElement?
    ): UAnnotation {
        return KotlinUAnnotation(annotationEntry, givenParent)
    }

    fun convertDeclaration(
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
                is KtLightMethod -> {
                    el<UMethod>(build(KotlinUMethod::create))
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

                is KtLightClass -> {
                    when (original.kotlinOrigin) {
                        is KtEnumEntry -> el<UEnumConstant> {
                            convertEnumEntry(original.kotlinOrigin as KtEnumEntry, givenParent)
                        }
                        else -> el<UClass> { KotlinUClass.create(original, givenParent) }
                    }
                }

                is KtLightField -> {
                    convertToUField(original, original.kotlinOrigin)
                }
                is KtLightFieldForSourceDeclarationSupport -> {
                    // KtLightFieldForDecompiledDeclaration is not a KtLightField
                    convertToUField(original, original.kotlinOrigin)
                }
                is KtLightParameter -> {
                    el<UParameter>(buildKtOpt(original.kotlinOrigin, ::KotlinUParameter))
                }
                is UastKotlinPsiParameter -> {
                    el<UParameter>(buildKt(original.ktParameter, ::KotlinUParameter))
                }
                is UastKotlinPsiParameterBase<*> -> {
                    el<UParameter> {
                        original.ktOrigin.safeAs<KtTypeReference>()?.let { convertReceiverParameter(it) }
                    }
                }
                is UastKotlinPsiVariable -> {
                    el<ULocalVariable>(buildKt(original.ktElement, ::KotlinULocalVariable))
                }

                is KtEnumEntry -> {
                    el<UEnumConstant> {
                        convertEnumEntry(original, givenParent)
                    }
                }
                is KtClassOrObject -> {
                    convertClassOrObject(original, givenParent, this).firstOrNull()
                }

                is KtFunction -> {
                    if (original.isLocal) {
                        el<ULambdaExpression> {
                            val parent = original.parent
                            if (parent is KtLambdaExpression) {
                                KotlinULambdaExpression(parent, givenParent) // your parent is the ULambdaExpression
                            } else if (original.name.isNullOrEmpty()) {
                                createLocalFunctionLambdaExpression(original, givenParent)
                            } else {
                                val uDeclarationsExpression = createLocalFunctionDeclaration(original, givenParent)
                                val localFunctionVar = uDeclarationsExpression.declarations.single() as KotlinLocalFunctionUVariable
                                localFunctionVar.uastInitializer
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

                is KtPropertyAccessor -> {
                    el<UMethod> {
                        val lightMethod = LightClassUtil.getLightClassAccessorMethod(original) ?: return null
                        convertDeclaration(lightMethod, givenParent, requiredTypes)
                    }
                }
                is KtProperty -> {
                    if (original.isLocal) {
                        convertPsiElement(original, givenParent, requiredTypes)
                    } else {
                        convertNonLocalProperty(original, givenParent, requiredTypes).firstOrNull()
                    }
                }
                is KtParameter -> {
                    convertParameter(original, givenParent, this).firstOrNull()
                }

                is KtFile -> {
                    convertKtFile(original, givenParent, this).firstOrNull()
                }
                is FakeFileForLightClass -> {
                    el<UFile> { KotlinUFile(original.navigationElement, languagePlugin) }
                }

                is KtAnnotationEntry -> {
                    el<UAnnotation>(build(::convertAnnotation))
                }
                is KtCallExpression -> {
                    if (requiredTypes.isAssignableFrom(KotlinUNestedAnnotation::class.java) &&
                        !requiredTypes.isAssignableFrom(UCallExpression::class.java)
                    ) {
                        el<UAnnotation> { KotlinUNestedAnnotation.create(original, givenParent) }
                    } else null
                }
                is KtLightAnnotationForSourceEntry -> {
                    convertDeclarationOrElement(original.kotlinOrigin, givenParent, requiredTypes)
                }
                is KtDelegatedSuperTypeEntry -> {
                    el<KotlinSupertypeDelegationUExpression> {
                        KotlinSupertypeDelegationUExpression(original, givenParent)
                    }
                }
                else -> null
            }
        }
    }

    fun convertDeclarationOrElement(
        element: PsiElement,
        givenParent: UElement?,
        expectedTypes: Array<out Class<out UElement>>
    ): UElement? {
        return if (element is UElement) element
        else convertDeclaration(element, givenParent, expectedTypes)
            ?: convertPsiElement(element, givenParent, expectedTypes)
    }

    fun convertKtFile(
        element: KtFile,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        return requiredTypes.accommodate(
            // File
            alternative { KotlinUFile(element, languagePlugin) },
            // Facade
            alternative { element.findFacadeClass()?.let { KotlinUClass.create(it, givenParent) } }
        )
    }

    fun convertClassOrObject(
        element: KtClassOrObject,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        val ktLightClass = element.toLightClass() ?: return emptySequence()
        val uClass = KotlinUClass.create(ktLightClass, givenParent)
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

    fun convertFakeLightConstructorAlternatives(
        original: UastFakeLightPrimaryConstructor,
        givenParent: UElement?,
        expectedTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        return expectedTypes.accommodate(
            alternative { convertDeclaration(original.original, givenParent, expectedTypes) as? UClass },
            alternative { KotlinConstructorUMethod(original.original, original, original.original, givenParent) }
        )
    }

    private fun getLightClassForFakeMethod(original: KtFunction): KtLightClass? {
        if (original.isLocal) return null
        return getContainingLightClass(original)
    }

    private fun convertToPropertyAlternatives(
        methods: LightClassUtil.PropertyAccessorsPsiMethods?,
        givenParent: UElement?
    ): Array<UElementAlternative<*>> {
        return if (methods != null)
            arrayOf(
                alternative { methods.backingField?.let { KotlinUField(it, getKotlinMemberOrigin(it), givenParent) } },
                alternative { methods.getter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } },
                alternative { methods.setter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } }
            )
        else emptyArray()
    }

    fun convertNonLocalProperty(
        property: KtProperty,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        return requiredTypes.accommodate(
            *convertToPropertyAlternatives(LightClassUtil.getLightClassPropertyMethods(property), givenParent)
        )
    }

    fun convertParameter(
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

    private fun convertEnumEntry(original: KtEnumEntry, givenParent: UElement?): UElement? {
        return LightClassUtil.getLightClassBackingField(original)?.let { psiField ->
            if (psiField is KtLightField && psiField is PsiEnumConstant) {
                KotlinUEnumConstant(psiField, psiField.kotlinOrigin, givenParent)
            } else {
                null
            }
        }
    }

    private fun convertReceiverParameter(receiver: KtTypeReference): UParameter? {
        val call = (receiver.parent as? KtCallableDeclaration) ?: return null
        if (call.receiverTypeReference != receiver) return null
        return call.toUElementOfType<UMethod>()?.uastParameters?.firstOrNull()
    }

    fun forceUInjectionHost(): Boolean

    fun convertExpression(
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
                        val destructuringAssignments = expression.entries.mapIndexed { i, entry ->
                            val psiFactory = KtPsiFactory(expression.project)
                            val initializer = psiFactory.createAnalyzableExpression(
                                "${tempAssignment.name}.component${i + 1}()",
                                expression.containingFile
                            )
                            initializer.destructuringDeclarationInitializer = true
                            KotlinULocalVariable(
                                UastKotlinPsiVariable.create(entry, tempAssignment.javaPsi, declarationsExpression, initializer),
                                entry,
                                declarationsExpression
                            )
                        }
                        declarations = listOf(tempAssignment) + destructuringAssignments
                    }
                }

                is KtStringTemplateExpression -> {
                    when {
                        forceUInjectionHost() || requiredTypes.contains(UInjectionHost::class.java) -> {
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

                is KtArrayAccessExpression -> expr<UArrayAccessExpression>(build(::KotlinUArrayAccessExpression))

                is KtThisExpression -> expr<UThisExpression>(build(::KotlinUThisExpression))
                is KtSuperExpression -> expr<USuperExpression>(build(::KotlinUSuperExpression))
                is KtCallableReferenceExpression -> expr<UCallableReferenceExpression>(build(::KotlinUCallableReferenceExpression))
                is KtClassLiteralExpression -> expr<UClassLiteralExpression>(build(::KotlinUClassLiteralExpression))
                is KtObjectLiteralExpression -> expr<UObjectLiteralExpression>(build(::KotlinUObjectLiteralExpression))
                is KtDotQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUQualifiedReferenceExpression))
                is KtSafeQualifiedExpression -> expr<UQualifiedReferenceExpression>(build(::KotlinUSafeQualifiedExpression))
                is KtSimpleNameExpression -> expr<USimpleNameReferenceExpression>(build(::KotlinUSimpleReferenceExpression))
                is KtCallExpression -> expr<UCallExpression>(build(::KotlinUFunctionCallExpression))

                is KtBinaryExpression -> {
                    if (expression.operationToken == KtTokens.ELVIS) {
                        expr<UExpressionList>(build(::createElvisExpression))
                    } else {
                        expr<UBinaryExpression>(build(::KotlinUBinaryExpression))
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

    fun convertStringTemplateEntry(
        entry: KtStringTemplateEntry,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression? {
        return with(requiredTypes) {
            if (entry is KtStringTemplateEntryWithExpression) {
                expr<UExpression> {
                    convertOrEmpty(entry.expression, givenParent)
                }
            } else {
                expr<ULiteralExpression> {
                    if (entry is KtEscapeStringTemplateEntry)
                        KotlinStringULiteralExpression(entry, givenParent, entry.unescapedValue)
                    else
                        KotlinStringULiteralExpression(entry, givenParent)
                }
            }
        }
    }

    fun convertVariablesDeclaration(
        psi: KtVariableDeclaration,
        parent: UElement?
    ): UDeclarationsExpression {
        val declarationsExpression = parent as? KotlinUDeclarationsExpression
            ?: psi.parent.toUElementOfType<UDeclarationsExpression>() as? KotlinUDeclarationsExpression
            ?: KotlinUDeclarationsExpression(
                null,
                parent,
                psi
            )
        val parentPsiElement = parent?.javaPsi //TODO: looks weird. mb look for the first non-null `javaPsi` in `parents` ?
        val variable =
            KotlinUAnnotatedLocalVariable(
                UastKotlinPsiVariable.create(psi, parentPsiElement, declarationsExpression),
                psi,
                declarationsExpression
            ) { annotationParent ->
                psi.annotationEntries.map { convertAnnotation(it, annotationParent) }
            }
        return declarationsExpression.apply { declarations = listOf(variable) }
    }

    fun convertWhenCondition(
        condition: KtWhenCondition,
        givenParent: UElement?,
        requiredType: Array<out Class<out UElement>>
    ): UExpression? {
        return with(requiredType) {
            when (condition) {
                is KtWhenConditionInRange -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpression(condition, givenParent).apply {
                        leftOperand = KotlinStringUSimpleReferenceExpression("it", this)
                        operator = when {
                            condition.isNegated -> KotlinBinaryOperators.NOT_IN
                            else -> KotlinBinaryOperators.IN
                        }
                        rightOperand = convertOrEmpty(condition.rangeExpression, this)
                    }
                }
                is KtWhenConditionIsPattern -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpressionWithType(condition, givenParent).apply {
                        operand = KotlinStringUSimpleReferenceExpression("it", this)
                        operationKind = when {
                            condition.isNegated -> KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK
                            else -> UastBinaryExpressionWithTypeKind.InstanceCheck.INSTANCE
                        }
                        typeReference = condition.typeReference?.let {
                            val service = ServiceManager.getService(BaseKotlinUastResolveProviderService::class.java)
                            KotlinUTypeReferenceExpression(it, this) { service.resolveToType(it, this, boxed = true) ?: UastErrorType }
                        }
                    }
                }

                is KtWhenConditionWithExpression ->
                    condition.expression?.let { convertExpression(it, givenParent, requiredType) }

                else -> expr<UExpression> { UastEmptyExpression(givenParent) }
            }
        }
    }

    fun convertPsiElement(
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
                is KtLambdaArgument -> {
                    element.getLambdaExpression()?.let { convertExpression(it, givenParent, requiredTypes) }
                }
                is KtLightElementBase -> {
                    when (val expression = element.kotlinOrigin) {
                        is KtExpression -> convertExpression(expression, givenParent, requiredTypes)
                        else -> el<UExpression> { UastEmptyExpression(givenParent) }
                    }
                }
                is KtLiteralStringTemplateEntry, is KtEscapeStringTemplateEntry -> {
                    el<ULiteralExpression>(build(::KotlinStringULiteralExpression))
                }
                is KtStringTemplateEntry -> {
                    element.expression?.let { convertExpression(it, givenParent, requiredTypes) }
                        ?: expr<UExpression> { UastEmptyExpression(givenParent) }
                }
                is KtWhenEntry -> {
                    el<USwitchClauseExpressionWithBody>(build(::KotlinUSwitchEntry))
                }
                is KtWhenCondition -> {
                    convertWhenCondition(element, givenParent, requiredTypes)
                }
                is KtTypeReference -> {
                    requiredTypes.accommodate(
                        alternative { KotlinUTypeReferenceExpression(element, givenParent) },
                        alternative { convertReceiverParameter(element) }
                    ).firstOrNull()
                }
                is KtConstructorDelegationCall -> {
                    el<UCallExpression> { KotlinUFunctionCallExpression(element, givenParent) }
                }
                is KtSuperTypeCallEntry -> {
                    el<UExpression> {
                        (element.getParentOfType<KtClassOrObject>(true)?.parent as? KtObjectLiteralExpression)
                            ?.toUElementOfType<UExpression>()
                            ?: KotlinUFunctionCallExpression(element, givenParent)
                    }
                }
                is KtImportDirective -> {
                    el<UImportStatement>(build(::KotlinUImportStatement))
                }
                is PsiComment -> {
                    el<UComment>(build(::UComment))
                }
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

    fun convertOrEmpty(expression: KtExpression?, parent: UElement?): UExpression {
        return expression?.let { convertExpression(it, parent, DEFAULT_EXPRESSION_TYPES_LIST) } ?: UastEmptyExpression(parent)
    }

    fun convertOrNull(expression: KtExpression?, parent: UElement?): UExpression? {
        return if (expression != null) convertExpression(expression, parent, DEFAULT_EXPRESSION_TYPES_LIST) else null
    }

    fun KtPsiFactory.createAnalyzableExpression(text: String, context: PsiElement): KtExpression =
        createAnalyzableProperty("val x = $text", context).initializer ?: error("Failed to create expression from text: '$text'")

    fun KtPsiFactory.createAnalyzableProperty(text: String, context: PsiElement): KtProperty =
        createAnalyzableDeclaration(text, context)

    fun <TDeclaration : KtDeclaration> KtPsiFactory.createAnalyzableDeclaration(text: String, context: PsiElement): TDeclaration {
        val file = createAnalyzableFile("dummy.kt", text, context)
        val declarations = file.declarations
        assert(declarations.size == 1) { "${declarations.size} declarations in $text" }
        @Suppress("UNCHECKED_CAST")
        return declarations.first() as TDeclaration
    }
}
