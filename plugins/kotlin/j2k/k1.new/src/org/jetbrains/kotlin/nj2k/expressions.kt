// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUnresolvedMethod
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKNoType
import org.jetbrains.kotlin.nj2k.types.JKType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.nj2k.types.replaceJavaClassWithKotlinClassType
import org.jetbrains.kotlin.resolve.annotations.KOTLIN_THROWS_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun JKOperator.isEquals() =
    token.safeAs<JKKtSingleValueOperatorToken>()?.psiToken in equalsOperators

private val equalsOperators =
    TokenSet.create(
        KtTokens.EQEQEQ,
        KtTokens.EXCLEQEQEQ,
        KtTokens.EQEQ,
        KtTokens.EXCLEQ
    )

fun untilToExpression(
    from: JKExpression,
    to: JKExpression,
    conversionContext: NewJ2kConverterContext
): JKExpression {
    val isPossibleToUseRangeUntil = conversionContext.converter.targetModule
        ?.let { it.languageVersionSettings.isPossibleToUseRangeUntil(it, conversionContext.project) } == true
    return rangeExpression(
        from,
        to,
        if (isPossibleToUseRangeUntil) "..<" else "until",
        conversionContext
    )
}

fun downToExpression(
    from: JKExpression,
    to: JKExpression,
    conversionContext: NewJ2kConverterContext
): JKExpression =
    rangeExpression(
        from,
        to,
        "downTo",
        conversionContext
    )

fun JKExpression.parenthesizeIfCompoundExpression() = when (this) {
    is JKIfElseExpression, is JKBinaryExpression, is JKTypeCastExpression -> JKParenthesizedExpression(this)
    else -> this
}

fun JKExpression.parenthesize() = JKParenthesizedExpression(this)

fun rangeExpression(
    from: JKExpression,
    to: JKExpression,
    operatorName: String,
    conversionContext: NewJ2kConverterContext
): JKExpression =
    JKBinaryExpression(
        from,
        to,
        JKKtOperatorImpl(
            JKKtWordOperatorToken(operatorName),
            conversionContext.symbolProvider.provideMethodSymbol("kotlin.ranges.$operatorName").returnType!!
        )
    )


fun blockStatement(vararg statements: JKStatement) =
    JKBlockStatement(JKBlockImpl(statements.toList()))

fun blockStatement(statements: List<JKStatement>) =
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
    val methodCall = JKCallExpressionImpl(useSymbol, listOf(lambda).toArgumentList())
    return JKQualifiedExpression(receiver, methodCall)
}

fun kotlinAssert(assertion: JKExpression, message: JKExpression?, typeFactory: JKTypeFactory) =
    JKCallExpressionImpl(
        JKUnresolvedMethod( //TODO resolve assert
            "assert",
            typeFactory,
            typeFactory.types.unit
        ),
        listOfNotNull(assertion, message).toArgumentList()
    )

fun jvmAnnotation(name: String, symbolProvider: JKSymbolProvider) =
    JKAnnotation(
        symbolProvider.provideClassSymbol("kotlin.jvm.$name")
    )

fun throwsAnnotation(throws: List<JKType>, symbolProvider: JKSymbolProvider) =
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

fun stringLiteral(content: String, typeFactory: JKTypeFactory): JKExpression {
    val lines = content.split('\n')
    return lines.mapIndexed { i, line ->
        val newlineSeparator = if (i == lines.size - 1) "" else "\\n"
        JKLiteralExpression("\"$line$newlineSeparator\"", JKLiteralExpression.LiteralType.STRING)
    }.reduce { acc: JKExpression, literalExpression: JKLiteralExpression ->
        JKBinaryExpression(acc, literalExpression, JKKtOperatorImpl(JKOperatorToken.PLUS, typeFactory.types.string))
    }
}

fun JKVariable.findUsages(scope: JKTreeElement, context: NewJ2kConverterContext): List<JKFieldAccessExpression> {
    val symbol = context.symbolProvider.provideUniverseSymbol(this)
    val usages = mutableListOf<JKFieldAccessExpression>()
    val searcher = object : RecursiveApplicableConversionBase(context) {
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
    searcher.runConversion(scope, context)
    return usages
}

internal fun JKTreeElement.forEachDescendant(action: (JKElement) -> Unit) {
    action(this)
    this.forEachChild { it.forEachDescendant(action) }
}

internal inline fun <reified E : JKElement> JKTreeElement.forEachDescendantOfType(crossinline action: (E) -> Unit) {
    forEachDescendant { if (it is E) action(it) }
}

fun JKExpression.unboxFieldReference(): JKFieldAccessExpression? = when {
    this is JKFieldAccessExpression -> this
    this is JKQualifiedExpression && receiver is JKThisExpression -> selector as? JKFieldAccessExpression
    else -> null
}

fun JKFieldAccessExpression.asAssignmentFromTarget(): JKKtAssignmentStatement? =
    parent.safeAs<JKKtAssignmentStatement>()?.takeIf { it.field == this }

fun JKFieldAccessExpression.isInDecrementOrIncrement(): Boolean =
    when (parent.safeAs<JKUnaryExpression>()?.operator?.token) {
        JKOperatorToken.PLUSPLUS, JKOperatorToken.MINUSMINUS -> true
        else -> false
    }

fun JKVariable.hasUsages(scope: JKTreeElement, context: NewJ2kConverterContext): Boolean =
    findUsages(scope, context).isNotEmpty()

fun JKVariable.hasWritableUsages(scope: JKTreeElement, context: NewJ2kConverterContext): Boolean =
    findUsages(scope, context).any {
        it.asAssignmentFromTarget() != null
                || it.isInDecrementOrIncrement()
    }

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
    return JKCallExpressionImpl(symbolProvider.provideMethodSymbol("kotlin.run"), JKArgumentList(lambda))
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
    typeArguments: List<JKTypeElement> = emptyList()
) = JKQualifiedExpression(
    this,
    JKCallExpressionImpl(
        symbol,
        JKArgumentList(arguments.map { JKArgumentImpl(it) }),
        JKTypeArgumentList(typeArguments)
    )
)

val JKStatement.statements: List<JKStatement>
    get() = when (this) {
        is JKBlockStatement -> block.statements
        else -> listOf(this)
    }

val JKElement.psi: PsiElement?
    get() = (this as? PsiOwner)?.psi

inline fun <reified Elem : PsiElement> JKElement.psi(): Elem? = (this as? PsiOwner)?.psi as? Elem

fun JKTypeElement.present(): Boolean = type != JKNoType

fun JKStatement.isEmpty(): Boolean = when (this) {
    is JKEmptyStatement -> true
    is JKBlockStatement -> block is JKBodyStub
    is JKExpressionStatement -> expression is JKStubExpression
    else -> false
}

fun JKInheritanceInfo.present(): Boolean =
    extends.isNotEmpty() || implements.isNotEmpty()

fun JKInheritanceInfo.supertypeCount(): Int =
    extends.size + implements.size

fun JKClass.isLocalClass(): Boolean =
    parent !is JKClassBody && parent !is JKFile && parent !is JKTreeRoot

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

const val EXPERIMENTAL_STDLIB_API_ANNOTATION = "kotlin.ExperimentalStdlibApi"

@ApiStatus.Internal
fun LanguageVersionSettings.isPossibleToUseRangeUntil(module: Module, project: Project): Boolean =
    areKotlinVersionsSufficientToUseRangeUntil(module, project) &&
            FqName(EXPERIMENTAL_STDLIB_API_ANNOTATION).asString() in getFlag(AnalysisFlags.optIn)

/**
 * Checks that compilerVersion and languageVersion (or -XXLanguage:+RangeUntilOperator) versions are high enough to use rangeUntil
 * operator.
 *
 * Note that this check is not enough. You also need to check for OptIn (because stdlib declarations are annotated with OptIn)
 */
@ApiStatus.Internal
fun LanguageVersionSettings.areKotlinVersionsSufficientToUseRangeUntil(module: Module, project: Project): Boolean {
    val compilerVersion = ExternalCompilerVersionProvider.get(module)
        ?: IdeKotlinVersion.opt(KotlinJpsPluginSettings.jpsVersion(project))
        ?: return false
    // `rangeUntil` is added to languageVersion 1.8 only since 1.7.20-Beta compiler
    return compilerVersion >= COMPILER_VERSION_WITH_RANGEUNTIL_SUPPORT && supportsFeature(LanguageFeature.RangeUntilOperator)
}

private val COMPILER_VERSION_WITH_RANGEUNTIL_SUPPORT = IdeKotlinVersion.get("1.7.20-Beta")

val JKAnnotationListOwner.hasAnnotations: Boolean
    get() = annotationList.annotations.isNotEmpty()