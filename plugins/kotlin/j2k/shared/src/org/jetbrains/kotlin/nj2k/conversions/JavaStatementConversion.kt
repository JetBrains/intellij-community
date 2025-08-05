// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKJavaDisjunctionType
import org.jetbrains.kotlin.nj2k.types.isNull
import org.jetbrains.kotlin.nj2k.types.isStringType
import org.jetbrains.kotlin.nj2k.types.updateNullability
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Converts Java-specific statements to Kotlin statements:
 *   * `assert` statement
 *   * `synchronized` statement
 *   * Try-with-resources or `try` with multiple `catch` blocks
 *   * A so-called "guard clause" of the form
 *   `if (param == null) throw new IllegalArgumentException("error message")` to a more idiomatic Kotlin
 *   `requireNotNull` call (disabled in basic mode)
 */
class JavaStatementConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKStatement) return recurse(element)
        return recurse(
            when (element) {
                is JKJavaAssertStatement -> convertAssert(element)
                is JKJavaSynchronizedStatement -> convertSynchronized(element)
                is JKJavaTryStatement -> convertTry(element)
                is JKIfElseStatement -> convertGuardStatement(element)
                else -> element
            }
        )
    }

    // If this is a null assertion like `assert s1 != null`, replace it with `checkNotNull(s1),
    // otherwise convert it to a regular Kotlin assert statement
    private fun convertAssert(element: JKJavaAssertStatement): JKStatement {
        var assertion = element::condition.detached()
        if (assertion is JKParenthesizedExpression) {
            // drop awkward parentheses around the first argument
            assertion = assertion::expression.detached()
        }
        val messageExpression =
            if (element.description is JKStubExpression) null
            else JKLambdaExpression(JKExpressionStatement(element::description.detached()))

        val expressionComparedToNull = assertion.expressionComparedToNull(isNegated = true)
            ?: return kotlinAssert(assertion, messageExpression, symbolProvider).asStatement().withFormattingFrom(element)
        val referencedVariable = (expressionComparedToNull as? JKFieldAccessExpression)?.identifier?.target as? JKLocalVariable
        val checkNotNullSymbol = symbolProvider.provideMethodSymbol("kotlin.checkNotNull")

        if (referencedVariable != null && element.occursImmediatelyAfterDeclarationInBlock(referencedVariable)) {
            // If the assertion occurs immediately after the variable declaration, merge them by wrapping the initializer in checkNotNull
            val initializerType = referencedVariable.initializer.calculateType(typeFactory)
            val arguments = listOfNotNull(referencedVariable.initializer.copyTreeAndDetach(), messageExpression).toArgumentList()
            referencedVariable.initializer = JKCallExpressionImpl(
                checkNotNullSymbol,
                arguments,
                expressionType = initializerType?.updateNullability(NotNull),
                canMoveLambdaOutsideParentheses = true
            )

            // effectively delete the original statement, since the assertion was merged into the variable declaration above
            return JKEmptyStatement()
        }

        // Otherwise, leave the variable declaration untouched and do an inplace replacement of the assertion
        return JKCallExpressionImpl(
            checkNotNullSymbol,
            arguments = listOfNotNull(expressionComparedToNull.detached(assertion), messageExpression).toArgumentList(),
            canMoveLambdaOutsideParentheses = true
        ).asStatement().withFormattingFrom(element)
    }

    private fun JKStatement.occursImmediatelyAfterDeclarationInBlock(declaration: JKDeclaration): Boolean {
        val parent = this.parent.safeAs<JKBlockImpl>() ?: return false
        if (parent != declaration.parent?.parent) return false
        val statements = parent.statements
        val declarationIndex =
            statements.indexOfFirst { it is JKDeclarationStatement && it.declaredStatements.singleOrNull() == declaration }
        if (declarationIndex < 0 || declarationIndex == statements.lastIndex) return false
        val expressionIndex = statements.indexOf(this)
        if (expressionIndex < declarationIndex) return false
        if (declarationIndex + 1 == expressionIndex) return true
        return statements.subList(declarationIndex + 1, expressionIndex).all { it.isEmpty() }
    }

    private fun JKExpression.expressionComparedToNull(isNegated: Boolean = false): JKExpression? {
        if (this !is JKBinaryExpression) return null
        if (isNegated && operator.token.text != "!=") return null
        if (!isNegated && operator.token.text != "==") return null

        val left = left
        val right = right
        val leftIsNull = left is JKLiteralExpression && left.isNull()
        val rightIsNull = right is JKLiteralExpression && right.isNull()
        if (leftIsNull == rightIsNull) return null
        return if (leftIsNull) right else left
    }

    private fun convertSynchronized(element: JKJavaSynchronizedStatement): JKExpressionStatement {
        element.invalidate()
        val lambdaBody = JKLambdaExpression(JKBlockStatement(element.body))
        return JKExpressionStatement(
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.synchronized"),
                JKArgumentList(element.lockExpression, lambdaBody),
                canMoveLambdaOutsideParentheses = true
            ).withFormattingFrom(element)
        )
    }

    private fun convertTry(element: JKJavaTryStatement): JKStatement =
        if (element.isTryWithResources) {
            convertTryStatementWithResources(element)
        } else {
            convertNoResourcesTryStatement(element)
        }

    private fun convertTryStatementWithResources(tryStatement: JKJavaTryStatement): JKStatement {
        val body =
            resourceDeclarationsToUseExpression(
                tryStatement.resourceDeclarations,
                JKBlockStatement(tryStatement::tryBlock.detached())
            )
        return if (tryStatement.finallyBlock !is JKBodyStub || tryStatement.catchSections.isNotEmpty()) {
            JKExpressionStatement(
                JKKtTryExpression(
                    JKBlockImpl(listOf(body)),
                    tryStatement::finallyBlock.detached(),
                    tryStatement.catchSections.flatMap(::convertCatchSection)
                )
            ).withFormattingFrom(tryStatement)
        } else body
    }

    private fun resourceDeclarationsToUseExpression(
        resourceDeclarations: List<JKJavaResourceElement>,
        innerStatement: JKStatement
    ): JKStatement =
        resourceDeclarations
            .reversed()
            .fold(innerStatement) { inner, element ->
                val (receiver, name) = when (element) {
                    is JKJavaResourceExpression -> element::expression.detached() to null
                    is JKJavaResourceDeclaration -> element.declaration::initializer.detached() to element.declaration::name.detached()
                }
                JKExpressionStatement(
                    useExpression(
                        receiver = receiver,
                        variableIdentifier = name,
                        body = inner,
                        symbolProvider = symbolProvider
                    )
                )
            }

    private fun convertCatchSection(javaCatchSection: JKJavaTryCatchSection): List<JKKtTryCatchSection> {
        javaCatchSection.block.detach(javaCatchSection)
        val typeElement = javaCatchSection.parameter.type
        val parameterTypes = typeElement.type.safeAs<JKJavaDisjunctionType>()?.disjunctions ?: listOf(typeElement.type)
        return parameterTypes.map { type ->
            val parameter = JKParameter(
                JKTypeElement(type.updateNullability(NotNull), typeElement.annotationList.copyTreeAndDetach()),
                javaCatchSection.parameter.name.copyTreeAndDetach()
            )
            JKKtTryCatchSection(
                parameter,
                javaCatchSection.block.copyTreeAndDetach()
            ).withFormattingFrom(javaCatchSection)
        }
    }

    private fun convertNoResourcesTryStatement(tryStatement: JKJavaTryStatement): JKStatement =
        JKExpressionStatement(
            JKKtTryExpression(
                tryStatement::tryBlock.detached(),
                tryStatement::finallyBlock.detached(),
                tryStatement.catchSections.flatMap(::convertCatchSection)
            )
        ).withFormattingFrom(tryStatement)

    /**
     * Replaces some if-then-throw statements with calls to `require` or `check`. For example, a statement like
     * `if (enabled) throw new IllegalArgumentException("must be enabled")` would become `requireNotNull(s1) { "must be enabled" }`.
     *
     * This conversion is analogous to `ReplaceGuardClauseWithFunctionCallInspection` and is disabled in basic mode.
     */
    private fun convertGuardStatement(ifElseStatement: JKIfElseStatement): JKStatement {
        if (context.settings.basicMode) return ifElseStatement

        val thenExpression = ifElseStatement.thenBranch.statements.singleOrNull() ?: return ifElseStatement
        if (thenExpression !is JKExpressionStatement) return ifElseStatement

        val thrownExpression = thenExpression.expression.safeAs<JKThrowExpression>()?.exception ?: return ifElseStatement
        if (thrownExpression !is JKNewExpression || thrownExpression.arguments.arguments.size > 1) {
            return ifElseStatement
        }

        val expressionComparedToNull = ifElseStatement.condition.expressionComparedToNull()
        val exceptionName = thrownExpression.identifier?.name
        val correspondingMethodName = when (exceptionName) {
            "IllegalArgumentException" -> if (expressionComparedToNull != null) "kotlin.requireNotNull" else "kotlin.require"
            "IllegalStateException" -> if (expressionComparedToNull != null) "kotlin.checkNotNull" else "kotlin.check"
            else -> return ifElseStatement
        }
        val exceptionArgument = thrownExpression.arguments.arguments.firstOrNull()
        if (exceptionArgument != null && exceptionArgument.value.calculateType(typeFactory)?.isStringType() != true) {
            return ifElseStatement
        }

        val messageExpression = if (exceptionArgument == null) null else
            JKLambdaExpression(JKExpressionStatement(exceptionArgument::value.detached()))
        val methodCallSymbol = symbolProvider.provideMethodSymbol(correspondingMethodName)

        val originalCondition = ifElseStatement::condition.detached()
        val negatedCondition = if (originalCondition is JKPrefixExpression && originalCondition.operator.token.text == "!") {
            val conditionExpression = originalCondition::expression.detached()
            if (conditionExpression is JKParenthesizedExpression) {
                // now that the `!` prefix has been removed, clean up any superfluous parentheses
                conditionExpression::expression.detached()
            } else {
                conditionExpression
            }
        } else {
            JKPrefixExpression(
                originalCondition.parenthesizeIfCompoundExpression(),
                JKKtOperatorImpl(JKOperatorToken.EXCL, typeFactory.types.boolean)
            )
        }.withFormattingFrom(originalCondition)

        val newCallExpression = if (expressionComparedToNull != null && ifElseStatement.elseBranch.isEmpty()) {
            JKCallExpressionImpl(
                methodCallSymbol,
                listOfNotNull(
                    expressionComparedToNull.detached(ifElseStatement.condition),
                    messageExpression
                ).toArgumentList(),
                canMoveLambdaOutsideParentheses = true
            )
        } else {
            JKCallExpressionImpl(
                methodCallSymbol,
                listOfNotNull(negatedCondition, messageExpression).toArgumentList(),
                canMoveLambdaOutsideParentheses = true
            )
        }.asStatement()

        val elseBranch = ifElseStatement::elseBranch.detached()
        return if (elseBranch.isEmpty()) {
            newCallExpression
        } else {
            val statements = if (elseBranch is JKBlockStatement) {
                // any newlines that should follow the else block will be attached to the new parent block statement
                elseBranch.statements.last().lineBreaksAfter = 0
                listOf(newCallExpression) + elseBranch.statements.map { it.detached(elseBranch.block) }
            } else {
                listOf(newCallExpression, elseBranch)
            }
            JKBlockStatementWithoutBrackets(statements).withFormattingFrom(ifElseStatement)
        }.withFormattingFrom(ifElseStatement)
    }
}
