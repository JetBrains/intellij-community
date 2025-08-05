// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.INTERFACE
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType
import org.jetbrains.kotlin.nj2k.types.JKNoType
import org.jetbrains.kotlin.nj2k.types.JKType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.nj2k.types.replaceJavaClassWithKotlinClassType
import org.jetbrains.kotlin.resolve.annotations.KOTLIN_THROWS_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun JKOperator.isEquals(): Boolean =
    token.safeAs<JKKtSingleValueOperatorToken>()?.psiToken in equalsOperators

private val equalsOperators: TokenSet =
    TokenSet.create(
        KtTokens.EQEQEQ,
        KtTokens.EXCLEQEQEQ,
        KtTokens.EQEQ,
        KtTokens.EXCLEQ
    )

context(_: KaSession)
fun untilToExpression(
    from: JKExpression,
    to: JKExpression,
    conversionContext: ConverterContext
): JKExpression {
    val isPossibleToUseRangeUntil = conversionContext.converter.targetModule?.languageVersionSettings?.isPossibleToUseRangeUntil() == true
    return rangeExpression(
        from,
        to,
        if (isPossibleToUseRangeUntil) JKOperatorToken.RANGE_UNTIL else JKOperatorToken.UNTIL,
        conversionContext
    )
}

context(_: KaSession)
fun downToExpression(
    from: JKExpression,
    to: JKExpression,
    conversionContext: ConverterContext
): JKExpression =
    rangeExpression(
        from,
        to,
        JKOperatorToken.DOWN_TO,
        conversionContext
    )

fun JKExpression.parenthesizeIfCompoundExpression(): JKExpression = when (this) {
    is JKIfElseExpression, is JKBinaryExpression, is JKTypeCastExpression, is JKUnaryExpression -> JKParenthesizedExpression(this)
    else -> this
}

fun JKExpression.parenthesize(): JKParenthesizedExpression = JKParenthesizedExpression(this)

fun JKBinaryExpression.parenthesizedWithFormatting(): JKParenthesizedExpression =
    JKParenthesizedExpression(
        JKBinaryExpression(::left.detached(), ::right.detached(), operator)
    ).withFormattingFrom(this)

context(_: KaSession)
fun rangeExpression(
    from: JKExpression,
    to: JKExpression,
    token: JKOperatorToken,
    conversionContext: ConverterContext
): JKExpression =
    JKBinaryExpression(
        from,
        to,
        JKKtOperatorImpl(
            token,
            conversionContext.symbolProvider.provideMethodSymbol("kotlin.ranges.${token.text}").returnType!!
        )
    )

fun blockStatement(vararg statements: JKStatement): JKBlockStatement =
    JKBlockStatement(JKBlockImpl(statements.toList()))

fun blockStatement(statements: List<JKStatement>): JKBlockStatement =
    JKBlockStatement(JKBlockImpl(statements))

fun useExpression(
    receiver: JKExpression,
    variableIdentifier: JKNameIdentifier?,
    body: JKStatement,
    symbolProvider: JKSymbolProvider
): JKExpression {
    val useSymbol = symbolProvider.provideMethodSymbol("kotlin.io.use")
    val lambdaParameter = if (variableIdentifier != null) JKParameter(JKTypeElement(JKNoType), variableIdentifier) else null
    val lambda = JKLambdaExpression(body, listOfNotNull(lambdaParameter))
    val methodCall = JKCallExpressionImpl(useSymbol, listOf(lambda).toArgumentList(), canMoveLambdaOutsideParentheses = true)
    return JKQualifiedExpression(receiver, methodCall)
}

fun kotlinAssert(assertion: JKExpression, message: JKExpression?, symbolProvider: JKSymbolProvider): JKCallExpressionImpl =
    JKCallExpressionImpl(
        symbolProvider.provideMethodSymbol("kotlin.assert"),
        listOfNotNull(assertion, message).toArgumentList(),
        canMoveLambdaOutsideParentheses = true
    )

fun jvmAnnotation(name: String, symbolProvider: JKSymbolProvider): JKAnnotation =
    JKAnnotation(
        symbolProvider.provideClassSymbol("kotlin.jvm.$name")
    )

fun throwsAnnotation(throws: List<JKType>, symbolProvider: JKSymbolProvider): JKAnnotation =
    JKAnnotation(
        symbolProvider.provideClassSymbol(KOTLIN_THROWS_ANNOTATION_FQ_NAME.asString()),
        throws.map {
            JKAnnotationParameterImpl(
                JKClassLiteralExpression(JKTypeElement(it), JKClassLiteralExpression.ClassLiteralType.KOTLIN_CLASS)
            )
        }
    )

fun JKAnnotationList.annotationByFqName(fqName: String): JKAnnotation? =
    annotations.firstOrNull { it.classSymbol.fqName == fqName }

fun annotationArgumentStringLiteral(content: String): JKExpression {
    val string = if (content.contains("\n")) "\n\"\"\"$content\"\"\"" else "\"$content\""
    return JKLiteralExpression(string, LiteralType.STRING)
}

context(_: KaSession)
fun JKVariable.findUsages(scope: JKTreeElement, context: ConverterContext): List<JKFieldAccessExpression> {
    val symbol = context.symbolProvider.provideUniverseSymbol(this)
    val usages = mutableListOf<JKFieldAccessExpression>()
    val searcher = object : RecursiveConversion(context) {
        context(_: KaSession)
        override fun applyToElement(element: JKTreeElement): JKTreeElement {
            if (element is JKExpression) {
                element.unboxFieldReference()?.also {
                    if (it.identifier == symbol) {
                        usages += it
                    }
                }
            }
            return recurse(element)
        }
    }
    searcher.run(scope, context)
    return usages
}

fun JKTreeElement.forEachDescendant(action: (JKElement) -> Unit) {
    action(this)
    this.forEachChild { it.forEachDescendant(action) }
}

inline fun <reified E : JKElement> JKTreeElement.forEachDescendantOfType(crossinline action: (E) -> Unit) {
    forEachDescendant { if (it is E) action(it) }
}

fun JKExpression.unboxFieldReference(): JKFieldAccessExpression? = when {
    this is JKFieldAccessExpression -> this
    this is JKQualifiedExpression && receiver is JKThisExpression -> selector as? JKFieldAccessExpression
    else -> null
}

fun JKFieldAccessExpression.isInDecrementOrIncrement(): Boolean =
    when (parent.safeAs<JKUnaryExpression>()?.operator?.token) {
        JKOperatorToken.PLUSPLUS, JKOperatorToken.MINUSMINUS -> true
        else -> false
    }

context(_: KaSession)
fun JKVariable.hasUsages(scope: JKTreeElement, context: ConverterContext): Boolean =
    findUsages(scope, context).isNotEmpty()

fun equalsExpression(left: JKExpression, right: JKExpression, typeFactory: JKTypeFactory) =
    JKBinaryExpression(
        left,
        right,
        JKKtOperatorImpl(
            JKOperatorToken.EQEQ,
            typeFactory.types.boolean
        )
    )

fun createCompanion(declarations: List<JKDeclaration>): JKClass =
    JKClass(
        JKNameIdentifier(""),
        JKInheritanceInfo(emptyList(), emptyList()),
        JKClass.ClassKind.COMPANION,
        JKTypeParameterList(),
        JKClassBody(declarations),
        JKAnnotationList(),
        emptyList(),
        JKVisibilityModifierElement(Visibility.PUBLIC),
        JKModalityModifierElement(Modality.FINAL)
    )

fun JKClass.getCompanion(): JKClass? =
    declarationList.firstOrNull { it is JKClass && it.classKind == JKClass.ClassKind.COMPANION } as? JKClass

fun JKClass.getOrCreateCompanionObject(): JKClass =
    getCompanion()
        ?: createCompanion(declarations = emptyList())
            .also { classBody.declarations += it }

fun runExpression(body: JKStatement, symbolProvider: JKSymbolProvider): JKExpression {
    val lambda = JKLambdaExpression(body)
    return JKCallExpressionImpl(
        symbolProvider.provideMethodSymbol("kotlin.run"),
        JKArgumentList(lambda),
        canMoveLambdaOutsideParentheses = true
    )
}

fun assignmentStatement(target: JKVariable, expression: JKExpression, symbolProvider: JKSymbolProvider): JKKtAssignmentStatement =
    JKKtAssignmentStatement(
        field = JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(target)),
        expression = expression,
        token = JKOperatorToken.EQ,
    )

fun JKAnnotationMemberValue.toExpression(symbolProvider: JKSymbolProvider): JKExpression {
    fun handleAnnotationParameter(element: JKTreeElement): JKTreeElement =
        when (element) {
            is JKClassLiteralExpression ->
                element.also {
                    element.literalType = JKClassLiteralExpression.ClassLiteralType.KOTLIN_CLASS
                }

            is JKTypeElement ->
                JKTypeElement(
                    element.type.replaceJavaClassWithKotlinClassType(symbolProvider),
                    element::annotationList.detached()
                )

            else -> applyRecursive(element, ::handleAnnotationParameter)
        }

    return handleAnnotationParameter(
        when (this) {
            is JKStubExpression -> this
            is JKAnnotation -> JKNewExpression(
                classSymbol,
                JKArgumentList(
                    arguments.map { argument ->
                        val value = argument.value.copyTreeAndDetach().toExpression(symbolProvider)
                        when (argument) {
                            is JKAnnotationNameParameter ->
                                JKNamedArgument(value, JKNameIdentifier(argument.name.value))

                            else -> JKArgumentImpl(value)
                        }

                    }
                )
            )

            is JKKtAnnotationArrayInitializerExpression ->
                JKKtAnnotationArrayInitializerExpression(initializers.map { it.detached(this).toExpression(symbolProvider) })

            is JKExpression -> this
            else -> error("Bad initializer")
        }
    ) as JKExpression
}

fun JKExpression.asLiteralTextWithPrefix(): String? = when {
    this is JKPrefixExpression
            && (operator.token == JKOperatorToken.MINUS || operator.token == JKOperatorToken.PLUS)
            && expression is JKLiteralExpression
    -> operator.token.text + expression.cast<JKLiteralExpression>().literal

    this is JKLiteralExpression -> literal
    else -> null
}

fun JKClass.primaryConstructor(): JKKtPrimaryConstructor? = classBody.declarations.firstIsInstanceOrNull()

fun List<JKExpression>.toArgumentList(): JKArgumentList =
    JKArgumentList(map { JKArgumentImpl(it) })

fun JKExpression.asStatement(): JKExpressionStatement =
    JKExpressionStatement(this)

fun <T : JKExpression> T.nullIfStubExpression(): T? =
    if (this is JKStubExpression) null
    else this

fun JKExpression.qualified(qualifier: JKExpression?) =
    if (qualifier != null && qualifier !is JKStubExpression) {
        JKQualifiedExpression(qualifier, this)
    } else this

fun JKExpression.callOn(
    symbol: JKMethodSymbol,
    arguments: List<JKExpression> = emptyList(),
    typeArguments: List<JKTypeElement> = emptyList(),
    expressionType: JKType? = null,
    canMoveLambdaOutsideParentheses: Boolean = false
): JKQualifiedExpression = JKQualifiedExpression(
    this,
    JKCallExpressionImpl(
        symbol,
        JKArgumentList(arguments.map { JKArgumentImpl(it) }),
        JKTypeArgumentList(typeArguments),
        expressionType,
        canMoveLambdaOutsideParentheses
    ),
    expressionType
)

val JKStatement.statements: List<JKStatement>
    get() = when (this) {
        is JKBlockStatement -> block.statements
        else -> listOf(this)
    }

val JKElement.psi: PsiElement?
    get() = (this as? PsiOwner)?.psi

inline fun <reified Elem : PsiElement> JKElement.psi(): Elem? =
    (this as? PsiOwner)?.psi as? Elem

fun JKTypeElement.isPresent(): Boolean = type != JKNoType

fun JKStatement.isEmpty(): Boolean = when (this) {
    is JKEmptyStatement -> true
    is JKBlockStatement -> block is JKBodyStub
    is JKExpressionStatement -> expression is JKStubExpression
    else -> false
}

fun JKInheritanceInfo.isPresent(): Boolean =
    extends.isNotEmpty() || implements.isNotEmpty()

fun JKInheritanceInfo.supertypeCount(): Int =
    extends.size + implements.size

fun JKClass.isLocalClass(): Boolean =
    parent !is JKClassBody && parent !is JKFile && parent !is JKTreeRoot

fun JKClass.isInterface(): Boolean =
    classKind == INTERFACE

fun JKMethod.isTopLevel(): Boolean =
    parent is JKFile || parent is JKTreeRoot

val JKClass.declarationList: List<JKDeclaration>
    get() = classBody.declarations

val JKTreeElement.identifier: JKSymbol?
    get() = when (this) {
        is JKFieldAccessExpression -> identifier
        is JKCallExpression -> identifier
        is JKClassAccessExpression -> identifier
        is JKPackageAccessExpression -> identifier
        is JKNewExpression -> classSymbol
        else -> null
    }

val JKClass.isObjectOrCompanionObject
    get() = classKind == JKClass.ClassKind.OBJECT || classKind == JKClass.ClassKind.COMPANION

private const val EXPERIMENTAL_STDLIB_API_ANNOTATION = "kotlin.ExperimentalStdlibApi"

private fun LanguageVersionSettings.isPossibleToUseRangeUntil(): Boolean {
    if (apiVersion >= ApiVersion.KOTLIN_1_9) return true
    if (languageVersion < LanguageVersion.KOTLIN_1_8) return false
    return FqName(EXPERIMENTAL_STDLIB_API_ANNOTATION).asString() in getFlag(AnalysisFlags.optIn)
}

val JKAnnotationListOwner.hasAnnotations: Boolean
    get() = annotationList.annotations.isNotEmpty()

/**
 * For example, return false for expression like `1 + \n 2` and true for one like `5 + 3 \n -2`
 */
internal fun JKBinaryExpression.recursivelyContainsNewlineBeforeOperator(): Boolean {
    fun JKExpression.recursivelyEndsWithNewline(): Boolean {
        if (hasLineBreakAfter) return true
        val lastChild = children.lastOrNull()?.safeAs<JKExpression>() ?: return false
        return lastChild.recursivelyEndsWithNewline()
    }

    val operator = operator.token.text
    if (operator == "&&" || operator == "||") {
        // && and || actually can start on a new line in Kotlin, unlike other operators
        return false
    }

    val left = this.left
    val right = this.right
    if (left.recursivelyEndsWithNewline()) {
        return true
    }
    if (left is JKBinaryExpression && left.recursivelyContainsNewlineBeforeOperator()) return true
    if (right is JKBinaryExpression && right.recursivelyContainsNewlineBeforeOperator()) return true
    return false
}

fun JKExpression.isAtomic(): Boolean {
    return this is JKQualifiedExpression ||
            this is JKKtWhenExpression ||
            this is JKCallExpression ||
            this is JKFieldAccessExpression ||
            this is JKLiteralExpression ||
            this is JKParenthesizedExpression
}