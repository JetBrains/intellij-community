// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Mutability.*
import org.jetbrains.kotlin.idea.base.util.codeUsageScope
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


// JPA and @Volatile fields should always be mutable
val MUTABLE_ANNOTATIONS = setOf("kotlin.concurrent.Volatile", "javax.persistence.Column", "jakarta.persistence.Column")