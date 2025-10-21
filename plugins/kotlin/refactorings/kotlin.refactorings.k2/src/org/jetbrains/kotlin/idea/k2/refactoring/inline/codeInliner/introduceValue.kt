// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeInliner
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.renderer.render

/**
 * Modifies [MutableCodeToInline] introducing a variable initialized by [value] and replacing all of [usages] with its use.
 * The variable must be initialized (and so the value is calculated) before any other code in [MutableCodeToInline].
 * @param value Value to use for variable initialization
 * @param valueType Type of the value
 * @param usages Usages to be replaced. This collection can be empty and in this case the actual variable is not needed.
 * But the expression [value] must be calculated because it may have side effects.
 * @param expressionToBeReplaced Expression to be replaced by the [MutableCodeToInline].
 * @param nameSuggestion Name suggestion for the variable.
 * @param safeCall If true, then the whole code must not be executed if the [value] evaluates to null.
 */
context(_: KaSession)
internal fun MutableCodeToInline.introduceValue(
    value: KtExpression,
    valueType: AbstractCodeInliner.TypeDescription?,
    usages: Collection<KtExpression>,
    expressionToBeReplaced: KtExpression,
    nameSuggestion: String? = null,
    safeCall: Boolean = false
) {
    assert(usages.all { it in this })

    val psiFactory = KtPsiFactory(value.project)

    fun replaceUsages(name: Name) {
        val nameInCode = psiFactory.createExpression(name.render())
        for (usage in usages) {
            // there can be parenthesis around the expression which will become unnecessary
            val usageToReplace = (usage.parent as? KtParenthesizedExpression) ?: usage
            replaceExpression(usageToReplace, nameInCode)
        }
    }

    fun suggestName(validator: (String) -> Boolean): Name {
        val name = if (nameSuggestion != null) {
            KotlinNameSuggester.suggestNameByName(nameSuggestion, validator)
        } else {
            with(KotlinNameSuggester()) {
                suggestExpressionNames(value).filter(validator).firstOrNull() ?: "t"
            }
        }
        return Name.identifier(name)
    }

    // checks that name is used (without receiver) inside code being constructed but not inside usages that will be replaced
    fun isNameUsed(name: String) = collectNameUsages(this, name).any { nameUsage -> usages.none { it.isAncestor(nameUsage) } }

    if (!safeCall) {
        if (usages.isNotEmpty()) {

            val name = suggestName { name ->
                !name.nameHasConflictsInScope(expressionToBeReplaced) && !isNameUsed(name)
            }

            val declaration = psiFactory.createDeclarationByPattern<KtVariableDeclaration>("val $0 = $1", name, value)
            statementsBefore.add(0, declaration)

            replaceUsages(name)
        } else {
            statementsBefore.add(0, value)
        }
    } else {
        val useIt = !isNameUsed(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
        val name = if (useIt) StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME else suggestName { !isNameUsed(it) }
        replaceUsages(name)

        mainExpression = psiFactory.buildExpression {
            appendExpression(value)
            if (valueType?.isMarkedNullable != false) {
                appendFixedText("?")
            }

            appendFixedText(".let {")

            if (!useIt) {
                appendName(name)
                appendFixedText("->")
            }

            appendExpressionsFromCodeToInline()
            appendFixedText("}")
        }

        statementsBefore.clear()
    }
}

context(_: KaSession)
fun String.nameHasConflictsInScope(expressionToBeReplaced: KtExpression): Boolean {
    val nameValidator =
        KotlinDeclarationNameValidator(expressionToBeReplaced, true, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE)
    return !nameValidator.validate(this)
}

private fun collectNameUsages(scope: MutableCodeToInline, name: String): List<KtSimpleNameExpression> {
    return scope.expressions.flatMap { expression ->
        expression.collectDescendantsOfType { it.getReceiverExpression() == null && it.getReferencedName() == name }
    }
}