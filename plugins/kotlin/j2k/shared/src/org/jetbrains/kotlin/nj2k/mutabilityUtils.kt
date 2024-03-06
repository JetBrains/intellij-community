// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Mutability.*
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.PLUSPLUS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.MINUSMINUS
import org.jetbrains.kotlin.nj2k.tree.Visibility.PRIVATE
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private fun JKFieldAccessExpression.asAssignmentFromTarget(): JKKtAssignmentStatement? =
    parent.safeAs<JKKtAssignmentStatement>()?.takeIf { it.field == this }

private fun JKFieldAccessExpression.asParenthesizedAssignmentFromTarget(): JKParenthesizedExpression? =
    parent.safeAs<JKParenthesizedExpression>()?.takeIf { it.parent is JKKtAssignmentStatement && it.expression == this }

private fun JKFieldAccessExpression.asQualifiedAssignmentFromTarget(): JKQualifiedExpression? =
    parent.safeAs<JKQualifiedExpression>()?.takeIf {
        val operatorToken = it.parent.safeAs<JKUnaryExpression>()?.operator?.token
        it.selector == this &&
                (it.parent is JKKtAssignmentStatement || operatorToken == PLUSPLUS || operatorToken == MINUSMINUS)
    }

private fun hasAssignmentsOnlyInIfElseConditionals(elements: List<JKElement>): Boolean {
    var ifElseStatements = elements.map { it.parentOfType<JKIfElseStatement>() }.distinct()
    if (ifElseStatements.any { it == null }) return false
    if (ifElseStatements.size == 1) return true
    // either separate if/else statements OR if -> else if statement
    val parentStatements = ifElseStatements.map {
        var parent = it
        while (parent?.parentOfType<JKIfElseStatement>() != null) {
            parent = parent.parentOfType<JKIfElseStatement>()
        }
        parent
    }.distinct()
    if (parentStatements.size != 1 || parentStatements[0] == null) {
        return false
    }

    var currentParent = parentStatements[0]
    var originalSize = ifElseStatements.size

    // in cases of multiple IfElse statements
    // iterate through all statements and ensure they're chaining if -> else if -> else
    while (ifElseStatements.isNotEmpty()) {
        ifElseStatements = ifElseStatements.filter {
            if (currentParent?.elseBranch.safeAs<JKIfElseStatement>() == it || it == parentStatements[0]) {
                currentParent = it
                return@filter false
            }
            return@filter true
        }
        if (originalSize == ifElseStatements.size) {
            return false
        }
        originalSize = ifElseStatements.size
    }
    return true
}

private fun hasAssignmentsOnlyInWhenBlock(elements: List<JKElement>): Boolean {
    val whenStatements = elements.map { it.parentOfType<JKKtWhenBlock>() }.distinct()
    return !(whenStatements.size > 1 || whenStatements.any { it == null })
}

private fun isAssignmentInLoop(elements: List<JKElement>): Boolean =
    elements.any { element ->
        element.parentOfType<JKForInStatement>() != null
                || element.parentOfType<JKWhileStatement>() != null || element.parentOfType<JKDoWhileStatement>() != null
    }

context(KtAnalysisSession)
private fun JKVariable.findWritableUsages(scope: JKTreeElement, context: NewJ2kConverterContext): List<JKFieldAccessExpression> =
    findUsages(scope, context).filter {
        it.asAssignmentFromTarget() != null
                || it.isInDecrementOrIncrement() || it.asQualifiedAssignmentFromTarget() != null
                || it.asParenthesizedAssignmentFromTarget() != null
    }.distinct()

context(KtAnalysisSession)
fun JKVariable.hasWritableUsages(scope: JKTreeElement, context: NewJ2kConverterContext): Boolean =
    findWritableUsages(scope, context).isNotEmpty()

context(KtAnalysisSession)
fun JKVariable.inferMutabilityFromWritableUsages(scope: JKTreeElement, context: NewJ2kConverterContext): Mutability {
    if (this !is JKLocalVariable && this !is JKField) return UNKNOWN
    // cannot find usages out of file scope. No parent class/method indicates a copy/paste or top level variable and we should continue
    if (this is JKField && visibility != PRIVATE && (parentOfType<JKClass>() != null || parentOfType<JKMethod>() != null)) return UNKNOWN
    val writableUsages = findWritableUsages(scope, context)
    val isWritable = when {
        isAssignmentInLoop(writableUsages) -> true
        initializer is JKStubExpression && writableUsages.isEmpty() -> true
        // ex. "val foo = foo = 5" where 2nd foo is already initialized elsewhere
        initializer is JKFieldAccessExpression && writableUsages.size == 1 && writableUsages[0].identifier.fqName == initializer.identifier?.fqName -> false
        initializer is JKStubExpression && writableUsages.size == 1 -> false
        initializer is JKStubExpression -> !(hasAssignmentsOnlyInIfElseConditionals(writableUsages) || hasAssignmentsOnlyInWhenBlock(
            writableUsages
        ))

        else -> writableUsages.isNotEmpty()
    }
    return if (isWritable) MUTABLE else IMMUTABLE
}

// JPA and @Volatile fields should always be mutable
val MUTABLE_ANNOTATIONS = setOf("kotlin.concurrent.Volatile", "javax.persistence.Column", "jakarta.persistence.Column")