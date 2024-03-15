// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKJavaDisjunctionType
import org.jetbrains.kotlin.nj2k.types.isNull
import org.jetbrains.kotlin.nj2k.types.updateNullability
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JavaStatementConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKStatement) return recurse(element)
        return recurse(
            when (element) {
                is JKJavaAssertStatement -> convertAssert(element)
                is JKJavaSynchronizedStatement -> convertSynchronized(element)
                is JKJavaTryStatement -> convertTry(element)
                else -> element
            }
        )
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

        val expressionComparedToNull = assertion.expressionComparedToNull()
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
                canExtractLastArgumentIfLambda = true
            )

            // effectively delete the original statement, since the assertion was merged into the variable declaration above
            return JKEmptyStatement()
        }

        // Otherwise, leave the variable declaration untouched and do an inplace replacement of the assertion
        return JKCallExpressionImpl(
            checkNotNullSymbol,
            arguments = listOfNotNull(expressionComparedToNull.detached(assertion), messageExpression).toArgumentList(),
            canExtractLastArgumentIfLambda = true
        ).asStatement().withFormattingFrom(element)
    }

    private fun JKExpression.expressionComparedToNull(): JKExpression? {
        if (this !is JKBinaryExpression) return null
        if (operator.token.text != "!=") return null

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
                canExtractLastArgumentIfLambda = true
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
}
