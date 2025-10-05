// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.computeMissingCases
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isNothingType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

object BranchedFoldingUtils {
    private val KtIfExpression.branches: List<KtExpression?> get() = ifBranchesOrThis()

    private fun KtExpression.ifBranchesOrThis(): List<KtExpression?> {
        if (this !is KtIfExpression) return listOf(this)
        return listOf(then) + `else`?.ifBranchesOrThis().orEmpty()
    }

    private fun KtTryExpression.tryBlockAndCatchBodies(): List<KtExpression?> = listOf(tryBlock) + catchClauses.map { it.catchBody }

    /**
     * Returns the last statement from the block of [branch] only if the statement is an assignment.
     */
    private fun getFoldableBranchedAssignment(branch: KtExpression?): KtBinaryExpression? {
        fun checkAssignment(expression: KtBinaryExpression): Boolean {
            if (expression.operationToken !in KtTokens.ALL_ASSIGNMENTS) return false

            val left = expression.left as? KtNameReferenceExpression ?: return false
            if (expression.right == null) return false

            val parent = expression.parent
            if (parent is KtBlockExpression) {
                return !KtPsiUtil.checkVariableDeclarationInBlock(parent, left.text)
            }

            return true
        }
        return (branch?.lastBlockStatementOrThis() as? KtBinaryExpression)?.takeIf(::checkAssignment)
    }

    /**
     * A function to lift the common left expression from assignment expressions in branches of if, when, or try [expression].
     *
     * Steps of lifting the common left expression:
     *   1. From if, when, or try [expression], visit each branch to collect all assignment expressions. If [expression] is not if, when,
     *      or try expression, this function just returns. (See [lift] function below)
     *   2. Uses [KtPsiFactory] to create a stand-alone expression that is the same as each right expression of collected assignments, and
     *      replaces each assignment expression with the created stand-alone expression.
     *      (See [KtBinaryExpression.replaceWithRHS] function below)
     *   3. Replaces [expression] with a new expression in a form of
     *      `(the common left expression from assignment expressions) ASSIGNMENT_OPERATION (branches with right expression)`
     */
    fun tryFoldToAssignment(expression: KtExpression) {
        var lhs: KtExpression? = null
        var op: String? = null
        val psiFactory = KtPsiFactory(expression.project)
        fun KtBinaryExpression.replaceWithRHS() {
            if (lhs == null || op == null) {
                lhs = left!!.copy() as KtExpression
                op = operationReference.text
            }

            val rhs = right!!
            if (rhs is KtLambdaExpression && this.parent !is KtBlockExpression) {
                replace(psiFactory.createSingleStatementBlock(rhs))
            } else {
                replace(rhs)
            }
        }

        fun lift(e: KtExpression?) {
            when (e) {
                is KtWhenExpression -> e.entries.forEach { entry ->
                    getFoldableBranchedAssignment(entry.expression)?.replaceWithRHS() ?: lift(entry.expression?.lastBlockStatementOrThis())
                }

                is KtIfExpression -> e.branches.forEach { branch ->
                    getFoldableBranchedAssignment(branch)?.replaceWithRHS() ?: lift(branch?.lastBlockStatementOrThis())
                }

                is KtTryExpression -> e.tryBlockAndCatchBodies().forEach {
                    getFoldableBranchedAssignment(it)?.replaceWithRHS() ?: lift(it?.lastBlockStatementOrThis())
                }
            }
        }
        lift(expression)
        if (lhs != null && op != null) {
            expression.replace(psiFactory.createExpressionByPattern("$0 $1 $2", lhs, op, expression))
        }
    }

    /**
     * Returns a set of assignments in recursive branches of if, when, or try expression (it checks nested branches as well).
     *
     * @param expression if, when, or try expression to analyze branches for assignments.
     * @return empty set if
     *   - [expression] is null,
     *   - [expression] has some missing cases (cases that no branches handle), or
     *   - The right expressions of assignments do not match each other
     *     (for the concept of "match", see [collectAssignmentsAndCheck] function below).
     *   Otherwise, the number of all assignments.
     */
    context(_: KaSession)
    fun getFoldableAssignmentsFromBranches(expression: KtExpression?): Set<KtBinaryExpression> {
        if (expression == null) return emptySet()
        val assignments = mutableSetOf<KtBinaryExpression>()

        /**
         * A function to collect assignments in branches of if, when, or try expression when it does not have missing cases and
         * right expressions of all assignments match.
         *
         * We say "two right expressions match" only when the operator functions for the two assignments match.
         * When there are nested if/when/try expressions in branches, it recursively collects the assignments as well.
         * @param e the if, when, or try expression.
         * @return true if it does not have any missing cases. Otherwise, false.
         */
        fun collectAssignmentsAndCheck(e: KtExpression?): Boolean = when (e) {
            is KtWhenExpression -> {
                val entries = e.entries
                // When the KtWhenExpression has missing cases with an else branch, we cannot fold it.
                if (e.hasMissingCases()) false
                else entries.isNotEmpty() && entries.all { entry ->
                    val assignment = getFoldableBranchedAssignment(entry.expression)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(entry.expression?.lastBlockStatementOrThis())
                }
            }

            is KtIfExpression -> {
                val branches = e.branches
                val elseBranch = branches.lastOrNull()?.getStrictParentOfType<KtIfExpression>()?.`else`
                branches.size > 1 && elseBranch != null && branches.all { branch ->
                    val assignment = getFoldableBranchedAssignment(branch)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(branch?.lastBlockStatementOrThis())
                }
            }

            is KtTryExpression -> {
                e.tryBlockAndCatchBodies().all {
                    val assignment = getFoldableBranchedAssignment(it)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(it?.lastBlockStatementOrThis())
                }
            }

            is KtCallExpression -> {
                e.expressionType?.isNothingType ?: false
            }

            is KtBreakExpression, is KtContinueExpression, is KtThrowExpression, is KtReturnExpression -> true

            else -> false
        }

        // Check if all assignment have right expressions that match each other.
        if (!collectAssignmentsAndCheck(expression)) return emptySet()
        val firstAssignment = assignments.firstOrNull { it.right?.isNull() != true } ?: assignments.firstOrNull() ?: return emptySet()
        val leftType = firstAssignment.left?.expressionType ?: return emptySet()
        val rightType = firstAssignment.right?.expressionType ?: return emptySet()
        val firstOperation = firstAssignment.operationReference.mainReference.resolve()
        if (assignments.any { assignment -> !checkAssignmentsMatch(firstAssignment, assignment, firstOperation, leftType, rightType) }) {
            return emptySet()
        }

        // If there is any child element of expression whose type is KtBinaryExpression for an assignment that does not have the right
        // expression matching the right expression of the firstAssignment, returns -1.
        if (expression.anyDescendantOfType<KtBinaryExpression>(predicate = { binaryExpression ->
                if (binaryExpression.operationToken !in KtTokens.ALL_ASSIGNMENTS) return@anyDescendantOfType false

                if (binaryExpression.getNonStrictParentOfType<KtFinallySection>() != null) {
                    checkAssignmentsMatch(firstAssignment, binaryExpression, firstOperation, leftType, rightType)
                } else {
                    binaryExpression !in assignments
                }
            })) {
            return emptySet()
        }
        return assignments
    }

    /**
     * Returns whether the binary assignment expressions [first] and [second] match or not.
     * We say they match when they satisfy the following conditions:
     *  1. They have the same left operands
     *  2. They have the same operation tokens
     *  3. No assignability errors are present
     *  4. It satisfies one of the following:
     *      - The left operand is nullable and the right is null
     *      - Their operations are the same
     *      - If we cannot find symbols of their operations
     *          - When [leftType] is a nullable type, types of right operands of [first] and [second] with non-nullability are the same
     *          - When [leftType] is a non-nullable type, types of right operands of [first] and [second] are both non-nullable, and
     *            they are the same.
     */
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun checkAssignmentsMatch(
        first: KtBinaryExpression,
        second: KtBinaryExpression,
        firstOperation: PsiElement?,
        leftType: KaType,
        nonNullableRightTypeOfFirst: KaType,
    ): Boolean {
        // Check if they satisfy the above condition 1 and 2.
        fun haveSameLeft(first: KtBinaryExpression, second: KtBinaryExpression): Boolean {
            val leftOfFirst = first.left ?: return false
            val leftOfSecond = second.left ?: return false
            val leftSymbolOfFirst = leftOfFirst.mainReference?.resolve() ?: return false
            val leftSymbolOfSecond = leftOfSecond.mainReference?.resolve() ?: return false
            return leftSymbolOfFirst == leftSymbolOfSecond
        }
        if (!haveSameLeft(first, second) || first.operationToken != second.operationToken) return false

        val secondRight = second.right
        // Check if they satisfy the above condition 3.
        if (secondRight != null && analyze(secondRight) {
                secondRight
                    .diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                    .isNotEmpty()
            }) return false

        // Check if they satisfy the first of condition 4.
        val isSecondRightNull = secondRight?.isNull()
        if (isSecondRightNull == true && leftType.isNullable) return true


        // Check if they satisfy the second of condition 4.
        if (firstOperation != null && firstOperation == second.operationReference.mainReference.resolve()) return true

        // Check if they satisfy the third and fourth of condition 4.
        val rightTypeOfSecond = second.right?.expressionType ?: return false
        if (!leftType.isNullable && rightTypeOfSecond.isNullable) return false
        val nonNullableRightTypeOfSecond = rightTypeOfSecond.withNullability(false)
        return nonNullableRightTypeOfFirst.semanticallyEquals(nonNullableRightTypeOfSecond) ||
                (first.operationToken == KtTokens.EQ && nonNullableRightTypeOfSecond.isSubtypeOf(leftType))
    }

    /**
     * A function to lift `return` from return expressions in branches of if, when, or try [expression].
     */
    fun foldToReturn(expression: KtExpression): KtExpression {
        fun KtReturnExpression.replaceWithReturned() {
            returnedExpression?.let { replace(it) }
        }

        fun lift(e: KtExpression?) {
            when (e) {
                is KtWhenExpression -> e.entries.forEach { entry ->
                    val entryExpr = entry.expression
                    getFoldableBranchedReturn(entryExpr)?.replaceWithReturned() ?: lift(entryExpr?.lastBlockStatementOrThis())
                }
                is KtIfExpression -> e.branches.forEach { branch ->
                    getFoldableBranchedReturn(branch)?.replaceWithReturned() ?: lift(branch?.lastBlockStatementOrThis())
                }
                is KtTryExpression -> e.tryBlockAndCatchBodies().forEach {
                    getFoldableBranchedReturn(it)?.replaceWithReturned() ?: lift(it?.lastBlockStatementOrThis())
                }
            }
        }
        lift(expression)
        return expression.replaced(KtPsiFactory(expression.project).createExpressionByPattern("return $0", expression))
    }

    /**
     * Returns a return-expression inside [branch] or itself when the returned expression in the return-expression can be lifted.
     * Otherwise, returns null.
     *
     * For example,
     *     if (foo) {
     *       return bar   // can be lifted -> this function will return `return bar`
     *     } else {
     *       return       // cannot be lifted because of the null returned expression -> this function will return `null`
     *     }
     */
    fun getFoldableBranchedReturn(branch: KtExpression?): KtReturnExpression? =
        (branch?.lastBlockStatementOrThis() as? KtReturnExpression)?.takeIf {
            it.returnedExpression != null &&
                    it.returnedExpression !is KtLambdaExpression &&
                    it.getTargetLabel() == null
        }

    data class FoldableReturns(val returnExpressions : List<KtReturnExpression>, val isFoldable : Boolean) {
        fun isNotEmpty(): Boolean = isFoldable && returnExpressions.isNotEmpty()

        companion object {
            val NotFoldable = FoldableReturns(emptyList(), false)
        }
    }

    /**
     * Returns a list of return-expressions inside expressions [branches] that are branches of a if, when, or try expression.
     * If there is any branch that we cannot lift its returned expression, this function returns an empty list with 'false' for
     * `isFoldable`.
     *
     * For example, it returns `return bar` and `return zoo` expressions for the following code:
     *     if (foo) {
     *       return bar
     *     } else {
     *       return zoo
     *     }
     *
     * It returns an empty list with 'false' for `isFoldable` for the following code because we cannot lift the return expression in the
     * else-branch:
     *     if (foo) {
     *       return bar   // can be lifted
     *     } else {
     *       return       // cannot be lifted because of the null returned expression
     *     }
     */
    context(_: KaSession)
    private fun getFoldableReturnsFromBranches(branches: List<KtExpression?>): FoldableReturns {
        val foldableReturns = mutableListOf<KtReturnExpression>()
        for (branch in branches) {
            val foldableBranchedReturn = getFoldableBranchedReturn(branch)
            if (foldableBranchedReturn != null) {
                foldableReturns.add(foldableBranchedReturn)
            } else {
                val currReturns = branch?.lastBlockStatementOrThis()?.let { getFoldableReturnsFromBranches(it) }
                    ?: return FoldableReturns.NotFoldable
                if (!currReturns.isFoldable) return FoldableReturns.NotFoldable
                foldableReturns += currReturns.returnExpressions
            }
        }
        return FoldableReturns(foldableReturns, true)
    }

    /**
     * Returns a list of return-expressions that can be lifted from if, when, or try expression [expression].
     *
     * It returns an empty list with `isFoldable = false` if [expression] is one of if, when, and try expressions and
     *  - [expression] doesn't have an else-branch, and it has a missing case, or
     *  - there is any branch that we cannot lift its returned expression
     *
     * It returns an empty list with `isFoldable = true` if [expression] is one of [KtBreakExpression], [KtContinueExpression],
     * [KtThrowExpression], and [KtCallExpression].
     */
    context(_: KaSession)
    fun getFoldableReturnsFromBranches(expression: KtExpression): FoldableReturns = when (expression) {
        is KtWhenExpression -> {
            val entries = expression.entries
            when {
                expression.hasMissingCases() -> FoldableReturns.NotFoldable
                entries.isEmpty() -> FoldableReturns.NotFoldable
                else -> getFoldableReturnsFromBranches(entries.map { it.expression })
            }
        }
        is KtIfExpression -> {
            val branches = expression.branches
            when {
                branches.isEmpty() -> FoldableReturns.NotFoldable
                branches.lastOrNull()?.getStrictParentOfType<KtIfExpression>()?.`else` == null -> FoldableReturns.NotFoldable
                else -> getFoldableReturnsFromBranches(branches)
            }
        }
        is KtTryExpression -> {
            if (expression.finallyBlock?.finalExpression?.let { getFoldableReturnsFromBranches(listOf(it)) }?.isNotEmpty() == true)
                FoldableReturns.NotFoldable
            else
                getFoldableReturnsFromBranches(expression.tryBlockAndCatchBodies())
        }
        is KtCallExpression -> {
            if (expression.expressionType?.isNothingType == true) FoldableReturns(emptyList(), true) else FoldableReturns.NotFoldable
        }
        is KtBreakExpression, is KtContinueExpression, is KtThrowExpression -> FoldableReturns(emptyList(), true)
        else -> FoldableReturns.NotFoldable
    }

    /**
     * Returns true if the when-expression has a missing case with else-branch.
     */
    context(_: KaSession)
    private fun KtWhenExpression.hasMissingCases(): Boolean =
        !KtPsiUtil.checkWhenExpressionHasSingleElse(this) && computeMissingCases().isNotEmpty()

    context(_: KaSession)
    private fun getFoldableReturns(branches: List<KtExpression?>): List<KtReturnExpression>? =
        branches.fold<KtExpression?, MutableList<KtReturnExpression>?>(mutableListOf()) { prevList, branch ->
            if (prevList == null) return@fold null
            val foldableBranchedReturn = getFoldableBranchedReturn(branch)
            if (foldableBranchedReturn != null) {
                prevList.add(foldableBranchedReturn)
            } else {
                val currReturns = getFoldableReturns(branch?.lastBlockStatementOrThis()) ?: return@fold null
                prevList += currReturns
            }
            prevList
        }


    context(_: KaSession)
    fun getFoldableReturns(expression: KtExpression?): List<KtReturnExpression>? = when (expression) {
        is KtWhenExpression -> {
            val entries = expression.entries
            when {
                expression.hasMissingCases() -> null
                entries.isEmpty() -> null
                else -> getFoldableReturns(entries.map { it.expression })
            }
        }
        is KtIfExpression -> {
            val branches = expression.branches
            when {
                branches.isEmpty() -> null
                branches.lastOrNull()?.getStrictParentOfType<KtIfExpression>()?.`else` == null -> null
                else -> getFoldableReturns(branches)
            }
        }
        is KtTryExpression -> {
            if (expression.finallyBlock?.finalExpression?.let { getFoldableReturns(listOf(it)) }?.isNotEmpty() == true)
                null
            else
                getFoldableReturns(expression.tryBlockAndCatchBodies())
        }
        is KtCallExpression -> {
            if (expression.expressionType?.isNothingType == true) emptyList() else null
        }
        is KtBreakExpression, is KtContinueExpression, is KtThrowExpression -> emptyList()
        else -> null
    }
}
