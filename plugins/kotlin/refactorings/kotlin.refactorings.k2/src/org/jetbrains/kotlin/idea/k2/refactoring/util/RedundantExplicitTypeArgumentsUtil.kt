package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

fun KaSession.areTypeArgumentsRedundant(
    typeArgumentList: KtTypeArgumentList,
    approximateFlexible: Boolean = false,
): Boolean {
    val callExpression = typeArgumentList.parent as? KtCallExpression ?: return false
    val newCallExpression = buildCallExpressionWithoutTypeArgs(callExpression) ?: return false
    return areTypeArgumentsEqual(callExpression, newCallExpression, approximateFlexible)
}

private fun buildCallExpressionWithoutTypeArgs(element: KtCallExpression): KtCallExpression? {
    val context = findContextToAnalyze(element) ?: return null
    val typeArgumentListRange = element.typeArgumentList?.textRange ?: return null
    val contextStartOffset = context.range.start

    val textWithoutTypeArgs = context.text.removeRange(
        typeArgumentListRange.start - contextStartOffset,
        typeArgumentListRange.end - contextStartOffset,
    )

    val (prefix, suffix) = if (context.parent !is KtClassBody) {
        "object Obj {" to "}"
    } else "" to ""

    val codeFragment = KtPsiFactory(
        element.project,
        markGenerated = false,
    ).createBlockCodeFragment("$prefix$textWithoutTypeArgs$suffix", context)

    return codeFragment.findElementAt(typeArgumentListRange.start + prefix.length - contextStartOffset)?.parentOfType()
}

private fun findContextToAnalyze(
    expression: KtExpression,
): KtExpression? {
    for (element in expression.parentsWithSelf) {
        when (element) {
            is KtFunctionLiteral -> continue
            is KtParameter -> continue
            is KtPropertyAccessor -> continue
            is KtProperty -> if (element.parent is KtClassBody) continue else return element
            is KtFunction -> if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) continue else return element
            is KtDeclaration -> return element
            else -> continue
        }

    }
    return null
}

private fun KaSession.areTypeArgumentsEqual(
    originalCallExpression: KtCallExpression,
    newCallExpression: KtCallExpression,
    approximateFlexible: Boolean,
): Boolean {
    val originalTypeArgs = originalCallExpression.resolveToCall()?.singleFunctionCallOrNull()?.typeArgumentsMapping ?: return false
    val newTypeArgs = newCallExpression.resolveToCall()?.singleFunctionCallOrNull()?.typeArgumentsMapping ?: return false
    return originalTypeArgs.size == newTypeArgs.size &&
            originalTypeArgs.values.zip(newTypeArgs.values).all { (originalType, newType) ->
                areTypesEqual(originalType, newType, approximateFlexible)
            }

}

private fun KaSession.areTypesEqual(
    type1: KaType,
    type2: KaType,
    approximateFlexible: Boolean,
): Boolean {
    return if (type1 is KaTypeParameterType && type2 is KaTypeParameterType) {
        type1.name == type2.name
    } else if (type1 is KaDefinitelyNotNullType && type2 is KaDefinitelyNotNullType) {
        areTypesEqual(type1.original, type2.original, approximateFlexible)
    } else {
        (approximateFlexible || type1.hasFlexibleNullability == type2.hasFlexibleNullability) &&
                type1.semanticallyEquals(type2)
    }
}
