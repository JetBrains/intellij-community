// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.*
import com.intellij.psi.JavaTokenType.SUPER_KEYWORD
import com.intellij.psi.JavaTokenType.THIS_KEYWORD
import com.intellij.psi.impl.light.LightRecordMethod
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.ChildRole
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.java.PsiClassObjectAccessExpressionImpl
import com.intellij.psi.impl.source.tree.java.PsiLabeledStatementImpl
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl
import com.intellij.psi.infos.MethodCandidateInfo
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.JavaPsiRecordUtil.getFieldForComponent
import com.intellij.psi.util.TypeConversionUtil.calcTypeForBinaryExpression
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK1Mode
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.j2k.content
import org.jetbrains.kotlin.j2k.Nullability.*
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClassLiteralExpression.ClassLiteralType
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType.*
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.Mutability.UNKNOWN
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JavaToJKTreeBuilder(
    private val symbolProvider: JKSymbolProvider,
    private val typeFactory: JKTypeFactory,
    private val referenceSearcher: ReferenceSearcher,
    private val importStorage: JKImportStorage,
    private val bodyFilter: ((PsiElement) -> Boolean)?,
    private val forInlining: Boolean
) {
    private val expressionTreeMapper = ExpressionTreeMapper()
    private val declarationMapper = DeclarationMapper(expressionTreeMapper, withBody = bodyFilter == null)
    private val formattingCollector = FormattingCollector()

    // Per-file property with collected nullability information.
    // Needs to be flushed before building the tree for each root element.
    private var nullabilityInfo: NullabilityInfo? = null

    companion object {
        private val LOG = Logger.getInstance("@org.jetbrains.kotlin.nj2k.JavaToJKTreeBuilder")
    }

    fun buildTree(psi: PsiElement, saveImports: Boolean): JKTreeRoot? {
        nullabilityInfo = null

        return when (psi) {
            is PsiJavaFile -> psi.toJK()
            is PsiReferenceExpression -> with(expressionTreeMapper) { psi.toJK(ignoreFacades = false) }
            is PsiExpression -> with(expressionTreeMapper) { psi.toJK() }
            is PsiStatement -> with(declarationMapper) { psi.toJK() }
            is PsiTypeParameter -> with(declarationMapper) { psi.toJK() }
            is PsiClass -> with(declarationMapper) { psi.toJK() }
            is PsiField -> with(declarationMapper) { psi.toJK() }
            is PsiMethod -> with(declarationMapper) { psi.toJK() }
            is PsiAnnotation -> with(declarationMapper) { psi.toJK() }
            is PsiImportList -> psi.toJK(saveImports)
            is PsiImportStatementBase -> psi.toJK(saveImports)
            is PsiJavaCodeReferenceElement ->
                if (psi.parent is PsiReferenceList) {
                    val factory = JavaPsiFacade.getInstance(psi.project).elementFactory
                    val type = factory.createType(psi)
                    JKTypeElement(type.toJK().updateNullabilityRecursively(NotNull))
                } else null

            else -> null
        }?.let { JKTreeRoot(it) }
    }

    private inner class ExpressionTreeMapper {
        fun PsiExpression?.toJK(): JKExpression {
            val expression = when (this) {
                null -> JKStubExpression()
                is PsiBinaryExpression -> toJK()
                is PsiPrefixExpression -> toJK()
                is PsiPostfixExpression -> toJK()
                is PsiLiteralExpression -> toJK()
                is PsiMethodCallExpression -> toJK()
                is PsiReferenceExpression -> toJK()
                is PsiNewExpression -> toJK()
                is PsiArrayAccessExpression -> toJK()
                is PsiTypeCastExpression -> toJK()
                is PsiParenthesizedExpression -> toJK()
                is PsiAssignmentExpression -> toJK()
                is PsiInstanceOfExpression -> toJK()
                is PsiThisExpression -> toJK()
                is PsiSuperExpression -> toJK()
                is PsiConditionalExpression -> toJK()
                is PsiPolyadicExpression -> toJK()
                is PsiArrayInitializerExpression -> toJK()
                is PsiLambdaExpression -> toJK()
                is PsiClassObjectAccessExpressionImpl -> toJK()
                is PsiSwitchExpression -> toJK()
                else -> createErrorExpression()
            }

            expression.psi = this
            return expression.withFormattingFrom(this)
        }

        private fun PsiClassObjectAccessExpressionImpl.toJK(): JKClassLiteralExpression {
            val type = operand.type.toJK().updateNullabilityRecursively(NotNull)
            val literalType = when (type) {
                is JKJavaPrimitiveType -> ClassLiteralType.JAVA_PRIMITIVE_CLASS
                is JKJavaVoidType -> ClassLiteralType.JAVA_VOID_TYPE
                else -> ClassLiteralType.JAVA_CLASS
            }
            return JKClassLiteralExpression(JKTypeElement(type), literalType)
        }

        private fun PsiSwitchExpression.toJK(): JKJavaSwitchExpression =
            JKJavaSwitchExpression(expression.toJK(), collectSwitchCases())

        private fun PsiInstanceOfExpression.toJK(): JKIsExpression {
            val pattern = pattern.safeAs<PsiTypeTestPattern>()
            val psiTypeElement = checkType ?: pattern?.checkType
            val type = psiTypeElement?.type?.toJK() ?: JKNoType
            val typeElement = with(declarationMapper) { JKTypeElement(type, psiTypeElement.annotationList()) }
            val expr = JKIsExpression(operand.toJK(), typeElement).also { it.withFormattingFrom(this) }
            val patternVariable = pattern?.patternVariable

            if (patternVariable != null) {
                val variable = (operand as? PsiReferenceExpression)?.resolve() as? PsiVariable
                val name = variable?.name ?: patternVariable.name
                val typeElementForPattern =
                    with(declarationMapper) { JKTypeElement(type, psiTypeElement.annotationList()) }
                // Executed for the side effect of binding the symbol to a valid target
                JKParameter(typeElementForPattern, JKNameIdentifier(name)).also {
                    symbolProvider.provideUniverseSymbol(patternVariable, it)
                    it.psi = this
                }
            }

            return expr
        }

        private fun PsiThisExpression.toJK(): JKThisExpression {
            val qualifierLabel = qualifier?.referenceName?.let { JKLabelText(JKNameIdentifier(it)) } ?: JKLabelEmpty()
            return JKThisExpression(qualifierLabel, type.toJK(), shouldBePreserved = !forInlining)
        }

        private fun PsiSuperExpression.toJK(): JKSuperExpression {
            val qualifyingType = qualifier?.resolve() as? PsiClass
            if (qualifyingType == null) {
                // Case 0: plain "super.foo()" call
                return JKSuperExpression(type.toJK())
            }

            // Java's qualified super call syntax "A.super.foo()" is represented by two different cases in Kotlin.
            // See https://kotlinlang.org/docs/inheritance.html#calling-the-superclass-implementation
            val isQualifiedSuperTypeCall = getContainingClass()?.supers?.contains(qualifyingType) == true
            var superTypeQualifier: JKClassSymbol? = null
            var outerTypeQualifier: JKLabel = JKLabelEmpty()

            if (isQualifiedSuperTypeCall) {
                // Case 1: "super<A>.foo()" for accessing the superclass of the current class
                superTypeQualifier = symbolProvider.provideDirectSymbol(qualifyingType) as? JKClassSymbol
            } else {
                // Case 2: "super@A.foo()" for accessing the superclass of the outer class
                outerTypeQualifier = qualifier?.referenceName?.let { JKLabelText(JKNameIdentifier(it)) } ?: outerTypeQualifier
            }

            return JKSuperExpression(type.toJK(), superTypeQualifier, outerTypeQualifier)
        }

        private fun PsiConditionalExpression.toJK(): JKIfElseExpression {
            val condition = this.condition.toJK().let {
                // Unwrap parentheses to avoid double-wrapping in Kotlin: `if ((condition))`
                if (it is JKParenthesizedExpression) it::expression.detached() else it
            }
            val expression = JKIfElseExpression(
                condition,
                thenExpression.toJK(),
                elseExpression.toJK(),
                type.toJK()
            )

            // Prettify formatting with inner line breaks:
            //  - put the close parenthesis of the resulting `if` expression on the same line as the condition
            //  - put both branches on separate lines
            if (expression.condition.lineBreaksAfter > 0 || expression.thenBranch.lineBreaksAfter > 0) {
                expression.condition.lineBreaksAfter = 0
                expression.thenBranch.lineBreaksBefore = 1
                expression.thenBranch.lineBreaksAfter = 1
                expression.elseBranch.lineBreaksBefore = 1
            }

            return expression
        }

        private fun PsiPolyadicExpression.toJK(): JKExpression {
            val token = JKOperatorToken.fromElementType(operationTokenType)
            val jkOperandsWithPsiTypes: List<Pair<JKExpression, PsiType?>> = operands.mapIndexed { i, operand ->
                val jkOperand = operand.toJK().withLineBreaksFrom(operand, copyLineBreaksBefore = i != 0)
                    .parenthesizeIfCompoundExpression()
                jkOperand to operand.type
            }

            val binaryExpression = jkOperandsWithPsiTypes.reduce { (left, leftType), (right, rightType) ->
                val psiType = calcTypeForBinaryExpression(leftType, rightType, operationTokenType, true)
                val jkType = psiType?.toJK() ?: typeFactory.types.nullableAny
                JKBinaryExpression(left, right, JKKtOperatorImpl(token, jkType)) to psiType
            }.first

            return if (jkOperandsWithPsiTypes.any { it.first.containsNewLine() }) {
                binaryExpression.parenthesize()
            } else {
                binaryExpression
            }
        }

        private fun PsiAssignmentExpression.toJK(): JKJavaAssignmentExpression =
            JKJavaAssignmentExpression(
                lExpression.toJK(),
                rExpression.toJK().withLineBreaksFrom(rExpression, copyLineBreaksBefore = true),
                createOperator(operationSign.tokenType, type)
            )

        private fun PsiBinaryExpression.toJK(): JKExpression {
            val token = when (operationSign.tokenType) {
                JavaTokenType.EQEQ, JavaTokenType.NE ->
                    when {
                        canKeepEqEq(lOperand, rOperand) -> JKOperatorToken.fromElementType(operationSign.tokenType)
                        operationSign.tokenType == JavaTokenType.EQEQ -> JKOperatorToken.fromElementType(KtTokens.EQEQEQ)
                        else -> JKOperatorToken.fromElementType(KtTokens.EXCLEQEQEQ)
                    }

                else -> JKOperatorToken.fromElementType(operationSign.tokenType)
            }
            return JKBinaryExpression(
                lOperand.toJK().withLineBreaksFrom(lOperand),
                rOperand.toJK().withLineBreaksFrom(rOperand, copyLineBreaksBefore = true),
                JKKtOperatorImpl(
                    token,
                    type?.toJK() ?: typeFactory.types.nullableAny
                )
            )
        }

        private fun PsiLiteralExpression.toJK(): JKExpression {
            require(this is PsiLiteralExpressionImpl)
            return when (literalElementType) {
                JavaTokenType.NULL_KEYWORD -> JKLiteralExpression("null", NULL)
                JavaTokenType.TRUE_KEYWORD -> JKLiteralExpression("true", BOOLEAN)
                JavaTokenType.FALSE_KEYWORD -> JKLiteralExpression("false", BOOLEAN)
                JavaTokenType.STRING_LITERAL -> JKLiteralExpression(text, STRING)
                JavaTokenType.TEXT_BLOCK_LITERAL -> JKLiteralExpression(text, TEXT_BLOCK)
                JavaTokenType.CHARACTER_LITERAL -> JKLiteralExpression(text, CHAR)
                JavaTokenType.INTEGER_LITERAL -> JKLiteralExpression(text, INT)
                JavaTokenType.LONG_LITERAL -> JKLiteralExpression(text, LONG)
                JavaTokenType.FLOAT_LITERAL -> JKLiteralExpression(text, FLOAT)
                JavaTokenType.DOUBLE_LITERAL -> JKLiteralExpression(text, DOUBLE)
                else -> createErrorExpression()
            }
        }

        private fun createOperator(elementType: IElementType, type: PsiType?) =
            JKKtOperatorImpl(
                JKOperatorToken.fromElementType(elementType),
                type?.toJK() ?: typeFactory.types.nullableAny
            )

        private fun PsiPrefixExpression.toJK(): JKExpression {
            val expression = operand.toJK()
            return when (operationSign.tokenType) {
                JavaTokenType.TILDE -> expression.callOn(symbolProvider.provideMethodSymbol("kotlin.Int.inv"))
                else -> JKPrefixExpression(expression, createOperator(operationSign.tokenType, type))
            }
        }

        private fun PsiPostfixExpression.toJK(): JKExpression =
            JKPostfixExpression(operand.toJK(), createOperator(operationSign.tokenType, type))

        private fun PsiLambdaExpression.toJK(): JKExpression {
            val parameters = with(declarationMapper) { parameterList.parameters.map { it.toJK() } }
            val statement = when (val body = body) {
                is PsiExpression -> JKExpressionStatement(body.toJK())
                is PsiCodeBlock -> JKBlockStatement(with(declarationMapper) { body.toJK() })
                else -> JKBlockStatement(JKBodyStub)
            }
            return JKLambdaExpression(statement, parameters, functionalType())
        }

        private fun PsiMethodCallExpression.getExplicitTypeArguments(): PsiReferenceParameterList {
            if (typeArguments.isNotEmpty()) return typeArgumentList

            val resolveResult = resolveMethodGenerics()
            if (resolveResult is MethodCandidateInfo && resolveResult.isApplicable) {
                val method = resolveResult.element
                if (method.isConstructor || !method.hasTypeParameters()) return typeArgumentList
            }

            return AddTypeArgumentsFix.addTypeArguments(this, null, false)
                ?.safeAs<PsiMethodCallExpression>()
                ?.typeArgumentList
                ?: typeArgumentList
        }

        //TODO mostly copied from old j2k, refactor
        private fun PsiMethodCallExpression.toJK(): JKExpression {
            val arguments = argumentList
            val typeArguments = getExplicitTypeArguments().toJK()
            val qualifier = methodExpression.qualifierExpression?.toJK()?.withLineBreaksFrom(methodExpression.qualifierExpression)
            var target = methodExpression.resolve()
            if (target is PsiMethodImpl && target.name.canBeGetterOrSetterName()) {
                val baseCallable = target.findSuperMethods().firstOrNull()
                if (baseCallable is KtLightMethod) {
                    target = baseCallable
                }
            }
            val symbol = target?.let {
                symbolProvider.provideDirectSymbol(it)
            } ?: JKUnresolvedMethod(methodExpression, typeFactory)

            return when {
                methodExpression.referenceNameElement is PsiKeyword -> {
                    val callee = when ((methodExpression.referenceNameElement as PsiKeyword).tokenType) {
                        SUPER_KEYWORD -> JKSuperExpression()
                        THIS_KEYWORD -> JKThisExpression(JKLabelEmpty(), JKNoType)
                        else -> createErrorExpression("unknown keyword in callee position")
                    }
                    val calleeSymbol = when {
                        symbol is JKMethodSymbol -> symbol
                        target is KtLightMethod -> KtClassImplicitConstructorSymbol(target, typeFactory)
                        else -> JKUnresolvedMethod(methodExpression, typeFactory)
                    }
                    JKDelegationConstructorCall(calleeSymbol, callee, arguments.toJK())
                }

                target is KtLightMethod -> {
                    when (val origin = target.kotlinOrigin) {
                        is KtNamedFunction -> {
                            if (origin.isExtensionDeclaration()) {
                                val receiver = arguments.expressions.firstOrNull()?.toJK()?.parenthesizeIfCompoundExpression()
                                origin.fqName?.also { importStorage.addImport(it) }
                                JKCallExpressionImpl(
                                    symbolProvider.provideDirectSymbol(origin) as JKMethodSymbol,
                                    arguments.expressions.drop(1).map { it.toJK() }.toArgumentList(),
                                    typeArguments
                                ).qualified(receiver)
                            } else {
                                origin.fqName?.also { importStorage.addImport(it) }
                                JKCallExpressionImpl(
                                    symbolProvider.provideDirectSymbol(origin) as JKMethodSymbol,
                                    arguments.toJK(),
                                    typeArguments
                                ).qualified(qualifier)
                            }
                        }

                        is KtProperty, is KtPropertyAccessor, is KtParameter -> {
                            origin.kotlinFqName?.also { importStorage.addImport(it) }
                            val property =
                                if (origin is KtPropertyAccessor) origin.parent as KtProperty
                                else origin as KtNamedDeclaration
                            val parameterCount = target.parameterList.parameters.size
                            val propertyAccessExpression =
                                JKFieldAccessExpression(symbolProvider.provideDirectSymbol(property) as JKFieldSymbol)
                            val isExtension = property.isExtensionDeclaration()
                            val isTopLevel = origin.getStrictParentOfType<KtClassOrObject>() == null
                            val propertyAccess = if (isTopLevel) {
                                if (isExtension) JKQualifiedExpression(
                                    arguments.expressions.first().toJK(),
                                    propertyAccessExpression
                                )
                                else propertyAccessExpression
                            } else propertyAccessExpression.qualified(qualifier)

                            when (if (isExtension) parameterCount - 1 else parameterCount) {
                                0 /* getter */ ->
                                    propertyAccess

                                1 /* setter */ -> {
                                    val index = if (isExtension) 1 else 0
                                    val (argument, type) = if (arguments.expressionCount > index) {
                                        arguments.expressions[index].toJK() to arguments.expressions[index].type
                                    } else {
                                        JKStubExpression() to null
                                    }
                                    JKJavaAssignmentExpression(
                                        propertyAccess,
                                        argument,
                                        createOperator(JavaTokenType.EQ, type)
                                    )
                                }

                                else -> createErrorExpression("expected getter or setter call")
                            }
                        }

                        else -> {
                            JKCallExpressionImpl(
                                JKMultiverseMethodSymbol(target, typeFactory),
                                arguments.toJK(),
                                typeArguments
                            ).qualified(qualifier)
                        }
                    }
                }

                target is LightRecordMethod -> {
                    val field = getFieldForComponent(target.recordComponent) ?: return createErrorExpression()
                    JKFieldAccessExpression(symbolProvider.provideDirectSymbol(field) as JKFieldSymbol)
                        .qualified(qualifier ?: JKThisExpression(JKLabelEmpty()))
                }

                symbol is JKMethodSymbol ->
                    JKCallExpressionImpl(symbol, arguments.toJK(), typeArguments)
                        .qualified(qualifier)

                symbol is JKFieldSymbol ->
                    JKFieldAccessExpression(symbol).qualified(qualifier)

                else -> createErrorExpression()
            }
        }

        private fun PsiFunctionalExpression.functionalType(): JKTypeElement =
            functionalInterfaceType
                ?.takeUnless { type ->
                    type.safeAs<PsiClassType>()?.parameters?.any { it is PsiCapturedWildcardType } == true
                }?.takeUnless { type ->
                    type.isKotlinFunctionalType
                }?.toJK()
                ?.asTypeElement() ?: JKTypeElement(JKNoType)

        fun PsiMethodReferenceExpression.toJK(): JKMethodReferenceExpression {
            val symbol = symbolProvider.provideSymbolForReference<JKSymbol>(this).let { symbol ->
                when {
                    symbol.isUnresolved && isConstructor -> JKUnresolvedClassSymbol(qualifier?.text ?: text, typeFactory)
                    symbol.isUnresolved && !isConstructor -> JKUnresolvedMethod(referenceName ?: text, typeFactory)
                    else -> symbol
                }
            }

            return JKMethodReferenceExpression(
                methodReferenceQualifier(),
                symbol,
                functionalType(),
                isConstructor
            )
        }

        private fun PsiMethodReferenceExpression.methodReferenceQualifier(): JKExpression {
            val qualifierType = qualifierType
            if (qualifierType != null) return JKTypeQualifierExpression(typeFactory.fromPsiType(qualifierType.type))

            return qualifierExpression?.toJK() ?: JKStubExpression()
        }

        fun PsiReferenceExpression.toJK(ignoreFacades: Boolean = true): JKExpression {
            if (this is PsiMethodReferenceExpression) return toJK()
            val target = resolve()

            if (ignoreFacades && (target is KtLightClassForFacade || target is KtLightClassForDecompiledDeclaration)) {
                // Normally, references to facade classes shouldn't be converted, because that would be
                // an unresolved reference in Kotlin: hence the `JKStubExpression`.
                // But in the case of copy-pasting a single facade class we handle it specially and just paste it as is.
                // It will still be an unresolved reference in Kotlin, but at least the user won't be surprised by disappearing code.
                return JKStubExpression()
            }

            if (target is KtLightField
                && target.name == "INSTANCE"
                && target.containingClass.kotlinOrigin is KtObjectDeclaration
            ) {
                return qualifierExpression?.toJK() ?: JKStubExpression()
            }

            val symbol = symbolProvider.provideSymbolForReference<JKSymbol>(this)
            return when (symbol) {
                is JKClassSymbol -> JKClassAccessExpression(symbol)
                is JKFieldSymbol -> JKFieldAccessExpression(symbol)
                is JKPackageSymbol -> JKPackageAccessExpression(symbol)
                is JKMethodSymbol -> JKMethodAccessExpression(symbol)
                is JKTypeParameterSymbol -> JKTypeQualifierExpression(JKTypeParameterType(symbol))
                else -> createErrorExpression()
            }.qualified(qualifierExpression?.toJK()).also {
                it.withFormattingFrom(this)
            }
        }

        private fun PsiArrayInitializerExpression.toJK(): JKExpression {
            val initializer = initializers.map { it.toJK().withLineBreaksFrom(it) }
            val type = JKTypeElement(type?.toJK().safeAs<JKJavaArrayType>()?.type ?: JKContextType)
            val hasTrailingComma = initializers.lastOrNull()?.trailingComma() != null
            return JKJavaNewArray(initializer, type, hasTrailingComma)
        }

        private fun PsiNewExpression.toJK(): JKExpression {
            require(this is PsiNewExpressionImpl)
            val newExpression =
                if (findChildByRole(ChildRole.LBRACKET) != null) {
                    arrayInitializer?.toJK() ?: run {
                        val dimensions = mutableListOf<JKExpression>()
                        var child = firstChild
                        while (child != null) {
                            if (child.node.elementType == JavaTokenType.LBRACKET) {
                                child = child.getNextSiblingIgnoringWhitespaceAndComments()
                                if (child.node.elementType == JavaTokenType.RBRACKET) {
                                    dimensions += JKStubExpression()
                                } else {
                                    child.safeAs<PsiExpression>()?.toJK()?.also { dimensions += it }
                                }
                            }
                            child = child.nextSibling
                        }
                        JKJavaNewEmptyArray(
                            dimensions,
                            JKTypeElement(generateSequence(type?.toJK()) { it.safeAs<JKJavaArrayType>()?.type }.last())
                        ).also {
                            it.psi = this
                        }
                    }
                } else {
                    val classSymbol =
                        classOrAnonymousClassReference?.resolve()?.let {
                            symbolProvider.provideDirectSymbol(it) as JKClassSymbol
                        } ?: JKUnresolvedClassSymbol(
                            classOrAnonymousClassReference?.referenceName ?: NO_NAME_PROVIDED,
                            typeFactory
                        )
                    val typeArgumentList =
                        this.typeArgumentList.toJK()
                            .takeIf { it.typeArguments.isNotEmpty() }
                            ?: classOrAnonymousClassReference
                                ?.typeParameters
                                ?.let { typeParameters ->
                                    JKTypeArgumentList(typeParameters.map { JKTypeElement(it.toJK()) })
                                } ?: JKTypeArgumentList()
                    JKNewExpression(
                        classSymbol,
                        argumentList?.toJK() ?: JKArgumentList(),
                        typeArgumentList,
                        with(declarationMapper) { anonymousClass?.createClassBody() } ?: JKClassBody(),
                        anonymousClass != null
                    ).also {
                        it.psi = this
                    }
                }

            return newExpression.qualified(qualifier?.toJK())
        }

        private fun PsiReferenceParameterList.toJK(): JKTypeArgumentList =
            JKTypeArgumentList(
                typeParameterElements.map { JKTypeElement(it.type.toJK(), with(declarationMapper) { it.annotationList() }) }
            ).also {
                it.withFormattingFrom(this)
            }

        private fun PsiArrayAccessExpression.toJK(): JKExpression =
            if (isK1Mode()) {
                // Leave the old K1 code to not break K1 nullability inference
                arrayExpression.toJK().callOn(
                    symbolProvider.provideMethodSymbol("kotlin.Array.get"),
                    arguments = listOf(indexExpression?.toJK() ?: JKStubExpression())
                )
            } else {
                JKArrayAccessExpression(arrayExpression.toJK(), indexExpression.toJK())
            }

        private fun PsiTypeCastExpression.toJK(): JKExpression {
            val expression = operand?.toJK() ?: createErrorExpression()
            val annotationList = with(declarationMapper) { castType.annotationList() }
            val type = (castType?.type?.toJK() ?: JKNoType).asTypeElement(annotationList)
            return JKTypeCastExpression(expression, type)
        }

        private fun PsiParenthesizedExpression.toJK(): JKExpression =
            JKParenthesizedExpression(expression.toJK(), shouldBePreserved = true)

        fun PsiExpressionList.toJK(): JKArgumentList {
            val jkExpressions = expressions.mapIndexed { i, argument ->
                argument.toJK().withLineBreaksFrom(argument, copyLineBreaksBefore = i == 0)
            }
            return ((parent as? PsiCall)?.resolveMethod()
                ?.let { method ->
                    val lastExpressionType = expressions.lastOrNull()?.type
                    if (jkExpressions.size == method.parameterList.parameters.size
                        && method.parameterList.parameters.getOrNull(jkExpressions.lastIndex)?.isVarArgs == true
                        && lastExpressionType is PsiArrayType
                    ) {
                        val staredExpression =
                            JKPrefixExpression(
                                jkExpressions.last(),
                                JKKtSpreadOperator(lastExpressionType.toJK())
                            ).withFormattingFrom(jkExpressions.last())
                        staredExpression.expression.also {
                            it.lineBreaksBefore = 0
                            it.lineBreaksAfter = 0
                        }
                        jkExpressions.dropLast(1) + staredExpression
                    } else jkExpressions
                } ?: jkExpressions)
                .toArgumentList()
                .also {
                    it.withFormattingFrom(this)
                }
        }
    }

    private fun PsiElement.trailingComma(): PsiElement? =
        getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it is PsiJavaToken && it.text == "," }

    private inner class DeclarationMapper(val expressionTreeMapper: ExpressionTreeMapper, var withBody: Boolean) {
        fun <R> withBodyGeneration(
            elementToCheck: PsiElement,
            trueBranch: DeclarationMapper.() -> R,
            elseBranch: DeclarationMapper.() -> R = trueBranch
        ): R = when {
            withBody -> trueBranch()
            bodyFilter?.invoke(elementToCheck) == true -> {
                withBody = true
                trueBranch().also { withBody = false }
            }

            else -> elseBranch()
        }

        fun PsiTypeParameterList.toJK(): JKTypeParameterList =
            JKTypeParameterList(typeParameters.map { it.toJK() })
                .also {
                    it.withFormattingFrom(this)
                }

        fun PsiTypeParameter.toJK(): JKTypeParameter =
            JKTypeParameter(
                nameIdentifier.toJK(),
                extendsListTypes.map { type -> JKTypeElement(type.toJK(), JKAnnotationList(type.annotations.map { it.toJK() })) },
                JKAnnotationList(annotations.mapNotNull { it?.toJK() })
            ).also {
                symbolProvider.provideUniverseSymbol(this, it)
                it.withFormattingFrom(this)
            }

        fun PsiClass.toJK(): JKClass {
            val jkClass = if (isRecord) toJKRecordClass() else toJKClass()
            jkClass.psi = this
            symbolProvider.provideUniverseSymbol(psi = this, jkClass)
            jkClass.withFormattingFrom(psi = this)
            return jkClass
        }

        private fun PsiClass.toJKClass(): JKClass =
            JKClass(
                nameIdentifier.toJK(),
                inheritanceInfo(),
                classKind(),
                typeParameterList?.toJK() ?: JKTypeParameterList(),
                createClassBody(),
                annotationList(this),
                otherModifiers(),
                visibility(),
                modality(),
                hasTrailingCommaAfterEnumEntries()
            )

        private fun PsiClass.toJKRecordClass(): JKRecordClass =
            JKRecordClass(
                nameIdentifier.toJK(),
                recordComponents(),
                inheritanceInfo(),
                typeParameterList?.toJK() ?: JKTypeParameterList(),
                createClassBody(),
                annotationList(this),
                otherModifiers(),
                visibility()
            )

        private fun PsiClass.recordComponents(): List<JKJavaRecordComponent> =
            recordComponents.map { component ->
                val psiTypeElement = component.typeElement
                val type = psiTypeElement?.type?.toJK() ?: JKNoType
                val typeElement = with(declarationMapper) { JKTypeElement(type, psiTypeElement.annotationList()) }
                JKJavaRecordComponent(
                    typeElement,
                    JKNameIdentifier(component.name),
                    component.isVarArgs,
                    component.annotationList(docCommentOwner = this)
                ).also {
                    it.withFormattingFrom(component)
                    symbolProvider.provideUniverseSymbol(component, it)
                    it.psi = component
                }
            }

        private fun PsiClass.hasTrailingCommaAfterEnumEntries(): Boolean =
            isEnum && childrenOfType<PsiEnumConstant>().lastOrNull()?.trailingComma() != null

        fun PsiClass.inheritanceInfo(): JKInheritanceInfo {
            val implementsTypes = implementsList?.referencedTypes?.map { type ->
                JKTypeElement(
                    type.toJK().updateNullability(NotNull),
                    JKAnnotationList(type.annotations.map { it.toJK() })
                )
            }.orEmpty()
            val extendsTypes = extendsList?.referencedTypes?.map { type ->
                JKTypeElement(
                    type.toJK().updateNullability(NotNull),
                    JKAnnotationList(type.annotations.map { it.toJK() })
                )
            }.orEmpty()
            return JKInheritanceInfo(extendsTypes, implementsTypes)
                .also {
                    if (implementsList != null) {
                        it.withFormattingFrom(implementsList!!)
                    }
                }
        }

        fun PsiClass.createClassBody() =
            JKClassBody(
                children.mapNotNull {
                    when (it) {
                        is PsiEnumConstant -> it.toJK()
                        is PsiClass -> it.toJK()
                        is PsiAnnotationMethod -> it.toJK()
                        is PsiMethod -> it.toJK()
                        is PsiField -> it.toJK()
                        is PsiClassInitializer -> it.toJK()
                        else -> null
                    }
                }
            ).also {
                it.leftBrace.withFormattingFrom(
                    lBrace,
                    copyCommentsAfter = false
                ) // do not capture comments which belongs to following declarations
                it.rightBrace.withFormattingFrom(rBrace)
                it.declarations.lastOrNull()?.let { lastMember ->
                    lastMember.withCommentsAfterWithParent(lastMember.psi)
                }
            }

        fun PsiClassInitializer.toJK(): JKDeclaration = when {
            hasModifier(JvmModifier.STATIC) -> JKJavaStaticInitDeclaration(body.toJK())
            else -> JKKtInitDeclaration(body.toJK())
        }.also {
            it.withFormattingFrom(this)
        }

        fun PsiEnumConstant.toJK(): JKEnumConstant =
            JKEnumConstant(
                nameIdentifier.toJK(),
                with(expressionTreeMapper) { argumentList?.toJK() ?: JKArgumentList() },
                initializingClass?.createClassBody() ?: JKClassBody(),
                JKTypeElement(
                    containingClass?.let { klass ->
                        JKClassType(symbolProvider.provideDirectSymbol(klass) as JKClassSymbol)
                    } ?: JKNoType
                ),
                annotationList(this),
            ).also {
                symbolProvider.provideUniverseSymbol(this, it)
                it.psi = this
                it.withFormattingFrom(this)
            }

        fun PsiMember.modality() =
            modality { ast, psi -> ast.withFormattingFrom(psi) }

        fun PsiMember.otherModifiers() =
            modifierList?.children?.mapNotNull { child ->
                if (child !is PsiKeyword) return@mapNotNull null
                when (child.text) {
                    PsiModifier.NATIVE -> OtherModifier.NATIVE
                    PsiModifier.STATIC -> OtherModifier.STATIC
                    PsiModifier.STRICTFP -> OtherModifier.STRICTFP
                    PsiModifier.SYNCHRONIZED -> OtherModifier.SYNCHRONIZED
                    PsiModifier.TRANSIENT -> OtherModifier.TRANSIENT
                    PsiModifier.VOLATILE -> OtherModifier.VOLATILE

                    else -> null
                }?.let {
                    JKOtherModifierElement(it).withFormattingFrom(child)
                }
            }.orEmpty()

        private fun PsiMember.visibility(): JKVisibilityModifierElement =
            visibility(referenceSearcher) { ast, psi -> ast.withFormattingFrom(psi) }

        fun PsiField.toJK(): JKField = JKField(
            JKTypeElement(type.toJK(), typeElement.annotationList()).withFormattingFrom(typeElement),
            nameIdentifier.toJK(),
            with(expressionTreeMapper) {
                withBodyGeneration(
                    this@toJK,
                    trueBranch = { initializer.toJK() },
                    elseBranch = { createTodoExpression() }
                )
            },
            annotationList(this),
            otherModifiers(),
            visibility(),
            modality(),
            JKMutabilityModifierElement(
                if (containingClass?.isInterface == true) IMMUTABLE else UNKNOWN
            )
        ).also {
            symbolProvider.provideUniverseSymbol(this, it)
            it.psi = this
            it.withFormattingFrom(this)
        }

        fun <T : PsiModifierListOwner> T.annotationList(docCommentOwner: PsiDocCommentOwner?): JKAnnotationList {
            val deprecatedAnnotation = docCommentOwner?.docComment?.deprecatedAnnotation()
            val plainAnnotations = annotations.mapNotNull { annotation ->
                when {
                    annotation !is PsiAnnotation -> null
                    annotation.qualifiedName == DEPRECATED_ANNOTATION_FQ_NAME && deprecatedAnnotation != null -> null
                    AnnotationTargetUtil.isTypeAnnotation(annotation) -> null
                    else -> annotation.toJK()
                }
            }
            return JKAnnotationList(plainAnnotations + listOfNotNull(deprecatedAnnotation))
        }

        fun PsiTypeElement?.annotationList(): JKAnnotationList {
            return JKAnnotationList(this?.applicableAnnotations?.map { it.toJK() }.orEmpty())
        }

        fun PsiAnnotation.toJK(): JKAnnotation {
            val symbol = when (val reference = nameReferenceElement) {
                null -> JKUnresolvedClassSymbol(NO_NAME_PROVIDED, typeFactory)
                else -> symbolProvider.provideSymbolForReference<JKSymbol>(reference).safeAs<JKClassSymbol>()
                    ?: JKUnresolvedClassSymbol(nameReferenceElement?.text ?: NO_NAME_PROVIDED, typeFactory)
            }
            return JKAnnotation(
                symbol,
                parameterList.attributes.map { parameter ->
                    if (parameter.nameIdentifier != null) {
                        JKAnnotationNameParameter(
                            parameter.value?.toJK() ?: JKStubExpression(),
                            JKNameIdentifier(parameter.name ?: NO_NAME_PROVIDED)
                        )
                    } else {
                        JKAnnotationParameterImpl(
                            parameter.value?.toJK() ?: JKStubExpression()
                        )
                    }
                }
            ).also {
                // Not saving line breaks here is a compromise.
                // It's easier to not save them here than to manually remove redundant line breaks
                // after various processings (for example, when properties are moved to primary constructor, etc.)
                // Usually, original line breaks in annotations are not important anyway.
                it.withFormattingFrom(this, copyLineBreaks = false)
            }
        }

        fun PsiDocComment.deprecatedAnnotation(): JKAnnotation? =
            findTagByName("deprecated")?.let { tag ->
                JKAnnotation(
                    symbolProvider.provideClassSymbol("kotlin.Deprecated"),
                    listOf(
                        JKAnnotationParameterImpl(annotationArgumentStringLiteral(tag.content()))
                    )
                )
            }

        private fun PsiAnnotationMemberValue.toJK(): JKAnnotationMemberValue =
            when (this) {
                is PsiExpression -> with(expressionTreeMapper) { toJK() }
                is PsiAnnotation -> toJK()
                is PsiArrayInitializerMemberValue ->
                    JKKtAnnotationArrayInitializerExpression(initializers.map { it.toJK() })

                else -> createErrorExpression()
            }.also {
                it.withFormattingFrom(this)
            }

        fun PsiAnnotationMethod.toJK(): JKJavaAnnotationMethod =
            JKJavaAnnotationMethod(
                JKTypeElement(
                    returnType?.toJK()
                        ?: JKJavaVoidType.takeIf { isConstructor }
                        ?: JKNoType,
                    returnTypeElement?.annotationList() ?: JKAnnotationList()
                ),
                nameIdentifier.toJK(),
                defaultValue?.toJK() ?: JKStubExpression(),
                annotationList(this),
                otherModifiers(),
                visibility(),
                modality()
            ).also {
                it.psi = this
                symbolProvider.provideUniverseSymbol(this, it)
                it.withFormattingFrom(this)
            }

        fun PsiMethod.toJK(): JKMethod {
            return JKMethodImpl(
                JKTypeElement(
                    returnType?.toJK()
                        ?: JKJavaVoidType.takeIf { isConstructor }
                        ?: JKNoType,
                    returnTypeElement.annotationList()),
                nameIdentifier.toJK(),
                parameterList.parameters.map { it.toJK().withLineBreaksFrom(it) },
                withBodyGeneration(this, trueBranch = { body?.toJK() ?: JKBodyStub }),
                typeParameterList?.toJK() ?: JKTypeParameterList(),
                annotationList(this),
                throwsList.referencedTypes.map { JKTypeElement(it.toJK()) },
                otherModifiers(),
                visibility(),
                modality()
            ).also { jkMethod ->
                jkMethod.psi = this
                jkMethod.hasRedundantVisibility = isOverrideMethodWithRedundantVisibility(jkMethod.visibility)
                symbolProvider.provideUniverseSymbol(this, jkMethod)
                parameterList.node
                    ?.safeAs<CompositeElement>()
                    ?.also {
                        jkMethod.leftParen.withFormattingFrom(it.findChildByRoleAsPsiElement(ChildRole.LPARENTH))
                        jkMethod.rightParen.withFormattingFrom(it.findChildByRoleAsPsiElement(ChildRole.RPARENTH))
                    }
            }.withFormattingFrom(this)
        }

        private fun PsiMethod.isOverrideMethodWithRedundantVisibility(visibility: Visibility): Boolean {
            val superMethods = findSuperMethods()
            if (superMethods.isEmpty()) return false // Unknown super method
            return superMethods.any { superMethod ->
                superMethod.overriddenMethodVisibility(referenceSearcher).visibility == visibility
            }
        }

        fun PsiParameter.toJK(): JKParameter {
            val rawType = type.toJK()
            val type =
                if (isVarArgs && rawType is JKJavaArrayType) JKTypeElement(rawType.type, typeElement.annotationList())
                else rawType.asTypeElement(typeElement.annotationList())
            val name = if (nameIdentifier != null) nameIdentifier.toJK() else JKNameIdentifier(name)

            val parameter = if (declarationScope is PsiForeachStatement) {
                JKForLoopParameter(type, name, annotationList = annotationList(null))
            } else {
                JKParameter(type, name, isVarArgs, annotationList = annotationList(null))
            }
            return parameter.also {
                symbolProvider.provideUniverseSymbol(this, it)
                it.psi = this
                it.withFormattingFrom(this)
            }
        }

        fun PsiCodeBlock.toJK(): JKBlock = JKBlockImpl(
            if (withBody) statements.map { it.toJK() } else listOf(createTodoExpression().asStatement())
        ).withFormattingFrom(this).also {
            it.leftBrace.withFormattingFrom(lBrace)
            it.rightBrace.withFormattingFrom(rBrace)
        }

        fun PsiLocalVariable.toJK(): JKLocalVariable {
            return JKLocalVariable(
                JKTypeElement(type.toJK(), typeElement.annotationList()).withFormattingFrom(typeElement),
                nameIdentifier.toJK(),
                with(expressionTreeMapper) { initializer.toJK().withLineBreaksFrom(initializer, copyLineBreaksBefore = true) },
                JKMutabilityModifierElement(
                    if (hasModifierProperty(PsiModifier.FINAL)) IMMUTABLE else UNKNOWN
                ),
                annotationList(null)
            ).also {
                symbolProvider.provideUniverseSymbol(this, it)
                it.psi = this
                it.withFormattingFrom(this)
            }
        }

        fun PsiStatement?.asJKStatementsList() = when (this) {
            null -> emptyList()
            is PsiExpressionListStatement -> expressionList.expressions.map { expression ->
                JKExpressionStatement(with(expressionTreeMapper) { expression.toJK() })
            }

            else -> listOf(toJK())
        }

        fun PsiStatement?.toJK(): JKStatement {
            return when (this) {
                null -> JKExpressionStatement(JKStubExpression())

                is PsiExpressionStatement -> JKExpressionStatement(with(expressionTreeMapper) { expression.toJK() })

                is PsiReturnStatement -> JKReturnStatement(with(expressionTreeMapper) { returnValue.toJK() })

                is PsiDeclarationStatement ->
                    JKDeclarationStatement(declaredElements.mapNotNull {
                        when (it) {
                            is PsiClass -> it.toJK()
                            is PsiLocalVariable -> it.toJK()
                            else -> null
                        }
                    })

                is PsiAssertStatement ->
                    JKJavaAssertStatement(
                        with(expressionTreeMapper) { assertCondition.toJK() },
                        with(expressionTreeMapper) { assertDescription?.toJK() } ?: JKStubExpression())

                is PsiIfStatement ->
                    with(expressionTreeMapper) {
                        JKIfElseStatement(condition.toJK(), thenBranch.toJK(), elseBranch.toJK())
                    }

                is PsiForStatement -> JKJavaForLoopStatement(
                    initialization.asJKStatementsList(),
                    with(expressionTreeMapper) { condition.toJK() },
                    update.asJKStatementsList(),
                    body.toJK()
                )

                is PsiForeachStatement ->
                    JKForInStatement(
                        iterationParameter.toJK() as JKForLoopParameter,
                        with(expressionTreeMapper) { iteratedValue?.toJK() ?: JKStubExpression() },
                        body?.toJK() ?: blockStatement()
                    )

                is PsiBlockStatement -> JKBlockStatement(codeBlock.toJK())

                is PsiWhileStatement -> JKWhileStatement(with(expressionTreeMapper) { condition.toJK() }, body.toJK())

                is PsiDoWhileStatement -> JKDoWhileStatement(body.toJK(), with(expressionTreeMapper) { condition.toJK() })

                is PsiSwitchStatement -> JKJavaSwitchStatement(with(expressionTreeMapper) { expression.toJK() }, collectSwitchCases())

                is PsiBreakStatement ->
                    JKBreakStatement(labelIdentifier?.let { JKLabelText(JKNameIdentifier(it.text)) } ?: JKLabelEmpty())

                is PsiContinueStatement -> {
                    val label = labelIdentifier?.let {
                        JKLabelText(JKNameIdentifier(it.text))
                    } ?: JKLabelEmpty()
                    JKContinueStatement(label)
                }

                is PsiLabeledStatement -> {
                    val (labels, statement) = collectLabels()
                    JKLabeledExpression(statement.toJK(), labels.map { JKNameIdentifier(it.text) }).asStatement()
                }

                is PsiEmptyStatement -> JKEmptyStatement()

                is PsiThrowStatement ->
                    JKThrowExpression(with(expressionTreeMapper) { exception.toJK() }).asStatement()

                is PsiTryStatement ->
                    JKJavaTryStatement(
                        resourceList?.toList()?.map { (it as PsiResourceListElement).toJK() }.orEmpty(),
                        tryBlock?.toJK() ?: JKBodyStub,
                        finallyBlock?.toJK() ?: JKBodyStub,
                        catchSections.map { it.toJK() }
                    )

                is PsiSynchronizedStatement ->
                    JKJavaSynchronizedStatement(
                        with(expressionTreeMapper) { lockExpression?.toJK() } ?: JKStubExpression(),
                        body?.toJK() ?: JKBodyStub
                    )

                is PsiYieldStatement -> JKJavaYieldStatement(with(expressionTreeMapper) { expression.toJK() })

                else -> createErrorStatement()
            }.also {
                if (this != null) {
                    (it as PsiOwner).psi = this
                    it.withFormattingFrom(this)
                    it.withLineBreaksFrom(this, copyLineBreaksBefore = it.commentsBefore.isNotEmpty())
                }
            }
        }

        fun PsiResourceListElement.toJK(): JKJavaResourceElement =
            when (this) {
                is PsiResourceVariable -> JKJavaResourceDeclaration((this as PsiLocalVariable).toJK())
                is PsiResourceExpression -> JKJavaResourceExpression(with(expressionTreeMapper) { this@toJK.expression.toJK() })
                else -> error("Unexpected resource list ${this::class.java}")
            }

        fun PsiCatchSection.toJK(): JKJavaTryCatchSection =
            JKJavaTryCatchSection(
                parameter?.toJK()
                    ?: JKParameter(JKTypeElement(JKNoType), JKNameIdentifier(NO_NAME_PROVIDED)),
                catchBlock?.toJK() ?: JKBodyStub
            ).also {
                it.psi = this
                it.withFormattingFrom(this)
            }

        private fun PsiLabeledStatement.collectLabels(): Pair<List<PsiIdentifier>, PsiStatement> {
            val labels = mutableListOf<PsiIdentifier>()
            var currentStatement: PsiStatement = this

            while (currentStatement is PsiLabeledStatementImpl) {
                labels += currentStatement.labelIdentifier
                currentStatement = currentStatement.statement ?: return labels to currentStatement
            }

            return labels to currentStatement
        }

        private fun createTodoExpression(): JKExpression =
            JKCallExpressionImpl(symbolProvider.provideMethodSymbol("kotlin.TODO"))

        private fun PsiElement.createErrorStatement(message: String? = null): JKStatement =
            JKErrorStatement(this, message)
    }

    private fun PsiJavaFile.toJK(): JKFile {
        collectNullabilityInfo(this)

        return JKFile(
            packageStatement?.toJK() ?: JKPackageDeclaration(JKNameIdentifier("")),
            importList.toJK(saveImports = false),
            with(declarationMapper) { classes.map { it.toJK() } }
        )
    }

    /**
     * See also [org.jetbrains.kotlin.nj2k.conversions.NullabilityConversion]
     * TODO support not only PsiJavaFile but any PsiElement
     */
    private fun collectNullabilityInfo(element: PsiJavaFile) {
        val nullityInferrer = J2KNullityInferrer()
        try {
            nullityInferrer.collect(element)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Throwable) {
            LOG.error(t)
        }

        nullabilityInfo = NullabilityInfo(
            nullityInferrer.nullableTypes,
            nullityInferrer.notNullTypes,
            nullityInferrer.nullableElements,
            nullityInferrer.notNullElements
        )
        typeFactory.nullabilityInfo = nullabilityInfo
    }

    private fun PsiImportList?.toJK(saveImports: Boolean): JKImportList =
        JKImportList(this?.allImportStatements?.mapNotNull { it.toJK(saveImports) }.orEmpty()).also { importList ->
            val innerComments = this?.collectDescendantsOfType<PsiComment>()?.map { comment ->
                JKComment(comment.text)
            }.orEmpty()
            importList.commentsBefore += innerComments
        }

    private fun PsiPackageStatement.toJK(): JKPackageDeclaration =
        JKPackageDeclaration(JKNameIdentifier(packageName))
            .also {
                it.withFormattingFrom(this)
                symbolProvider.provideUniverseSymbol(this, it)
            }

    private fun PsiImportStatementBase.toJK(saveImports: Boolean): JKImportStatement? {
        val target = when (this) {
            is PsiImportStaticStatement -> resolveTargetClass()
            else -> resolve()
        }
        val rawName = (importReference?.canonicalText ?: return null) + if (isOnDemand) ".*" else ""

        // We will save only unresolved imports and print all static calls with fqNames
        // to avoid name clashes in future
        if (!saveImports) {
            return if (target == null)
                JKImportStatement(JKNameIdentifier(rawName))
            else null
        }

        fun KtLightClassForDecompiledDeclaration.fqName(): FqName =
            kotlinOrigin?.fqName ?: FqName(qualifiedName.orEmpty())

        val name =
            target.safeAs<KtLightElement<*, *>>()?.kotlinOrigin?.kotlinFqName?.asString()
                ?: target.safeAs<KtLightClass>()?.containingFile?.safeAs<KtFile>()?.packageFqName?.asString()?.let { "$it.*" }
                ?: target.safeAs<KtLightClassForFacade>()?.facadeClassFqName?.parent()?.asString()?.let { "$it.*" }
                ?: target.safeAs<KtLightClassForDecompiledDeclaration>()?.fqName()?.parent()?.asString()?.let { "$it.*" }
                ?: rawName

        return JKImportStatement(JKNameIdentifier(name)).also {
            it.withFormattingFrom(this)
        }
    }

    private fun PsiIdentifier?.toJK(): JKNameIdentifier = this?.let {
        JKNameIdentifier(it.text).also { identifier ->
            identifier.withFormattingFrom(this)
        }
    } ?: JKNameIdentifier("")

    private fun PsiType?.toJK(): JKType =
        if (this == null) JKNoType else typeFactory.fromPsiType(this)

    private fun PsiSwitchBlock.collectSwitchCases(): List<JKJavaSwitchCase> = with(declarationMapper) {
        val statements = body?.statements ?: return emptyList()
        val cases = mutableListOf<JKJavaSwitchCase>()
        for (statement in statements) {
            when (statement) {
                is PsiSwitchLabelStatement ->
                    cases += when {
                        statement.isDefaultCase -> JKJavaDefaultSwitchCase(emptyList())
                        else -> JKJavaClassicLabelSwitchCase(
                            with(expressionTreeMapper) {
                                statement.caseLabelElementList?.elements?.map { (it as? PsiExpression).toJK() }.orEmpty()
                            },
                            emptyList()
                        )
                    }.withFormattingFrom(statement)

                is PsiSwitchLabeledRuleStatement -> {
                    val body = statement.body.toJK()
                    cases += when {
                        statement.isDefaultCase -> JKJavaDefaultSwitchCase(listOf(body))
                        else -> {
                            JKJavaArrowSwitchLabelCase(
                                with(expressionTreeMapper) {
                                    statement.caseLabelElementList?.elements?.map { (it as? PsiExpression).toJK() }.orEmpty()
                                },
                                listOf(body),
                            )
                        }
                    }.withFormattingFrom(statement)
                }

                else ->
                    cases.lastOrNull()?.also { it.statements += statement.toJK() } ?: run {
                        cases += JKJavaClassicLabelSwitchCase(
                            listOf(JKStubExpression()),
                            listOf(statement.toJK())
                        )
                    }
            }
        }
        return cases
    }

    private fun PsiElement.createErrorExpression(message: String? = null): JKExpression =
        JKErrorExpression(this, message)

    private fun <T : JKFormattingOwner> T.withFormattingFrom(
        psi: PsiElement?,
        copyLineBreaks: Boolean = true,
        copyLineBreaksBefore: Boolean = false,
        copyCommentsBefore: Boolean = true,
        copyCommentsAfter: Boolean = psi?.takeCommentsAfterNeeded() ?: false
    ): T = with(formattingCollector) {
        copyFormattingFrom(this@withFormattingFrom, psi, copyLineBreaks, copyLineBreaksBefore, copyCommentsBefore, copyCommentsAfter)
        this@withFormattingFrom
    }

    // we don't want to capture comments of next declaration/statement
    // unless it is the last declaration in scope
    private fun PsiElement.takeCommentsAfterNeeded(): Boolean = when (this) {
        is PsiMember -> getNextSiblingIgnoringWhitespaceAndComments() !is PsiMember
        is PsiStatement -> false
        else -> true
    }

    private fun <O : JKFormattingOwner> O.withLineBreaksFrom(psi: PsiElement?, copyLineBreaksBefore: Boolean = false) =
        with(formattingCollector) {
            copyLineBreaksFrom(this@withLineBreaksFrom, psi, copyLineBreaksBefore)
            this@withLineBreaksFrom
        }

    private fun <O : JKFormattingOwner> O.withCommentsAfterWithParent(psi: PsiElement?) = with(formattingCollector) {
        if (psi == null) return@with this@withCommentsAfterWithParent
        this@withCommentsAfterWithParent.commentsAfter += psi.commentsAfterWithParent()
        return this@withCommentsAfterWithParent
    }
}

private const val DEPRECATED_ANNOTATION_FQ_NAME = "java.lang.Deprecated"
private const val NO_NAME_PROVIDED = "NO_NAME_PROVIDED"
