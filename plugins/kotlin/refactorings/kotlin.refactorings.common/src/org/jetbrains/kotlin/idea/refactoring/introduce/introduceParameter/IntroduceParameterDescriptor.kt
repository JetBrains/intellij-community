// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.introduce.mustBeParenthesizedInInitializerPosition
import org.jetbrains.kotlin.idea.refactoring.introduce.substringContextOrThis
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.util.*

data class IntroduceParameterDescriptor<Descriptor>(
  val originalRange: KotlinPsiRange,
  val callable: KtNamedDeclaration,
  val callableDescriptor: Descriptor,
  val newParameterName: String,
  val newParameterTypeText: String,
  val argumentValue: KtExpression?,
  val withDefaultValue: Boolean,
  val parametersUsages: MultiMap<KtElement, KtElement>,
  val occurrencesToReplace: List<KotlinPsiRange>,
  val parametersToRemove: List<KtElement> = getParametersToRemove(withDefaultValue, parametersUsages, occurrencesToReplace),
  val occurrenceReplacer: IntroduceParameterDescriptor<Descriptor>.(KotlinPsiRange) -> Unit = {}
) {
    val newArgumentValue: KtExpression? by lazy {
        if (argumentValue != null && argumentValue.mustBeParenthesizedInInitializerPosition()) {
            KtPsiFactory(callable.project).createExpressionByPattern("($0)", argumentValue)
        } else {
            argumentValue
        }
    }

    val originalOccurrence: KotlinPsiRange
        get() = occurrencesToReplace.first { remapRange(it).textRange.intersects(remapRange(originalRange).textRange) }

    private fun remapRange(range: KotlinPsiRange): KotlinPsiRange =
        if (range is KotlinPsiRange.ListRange) KotlinPsiRange.ListRange(range.elements.map { it.substringContextOrThis }) else range

    var valVar: KotlinValVar = if (callable is KtClass) {
        val modifierIsUnnecessary: (PsiElement) -> Boolean = {
            when {
                it.parent != callable.body -> false
                it is KtAnonymousInitializer -> true
                it is KtProperty && it.initializer?.textRange?.intersects(originalRange.textRange) == true -> true
                else -> false
            }
        }

        if (occurrencesToReplace.all { PsiTreeUtil.findCommonParent(it.elements)?.parentsWithSelf?.any(modifierIsUnnecessary) == true })
            KotlinValVar.None
        else
            KotlinValVar.Val
    } else
        KotlinValVar.None

}

private fun getParametersToRemove(
    withDefaultValue: Boolean,
    parametersUsages: MultiMap<KtElement, KtElement>,
    occurrencesToReplace: List<KotlinPsiRange>
): List<KtElement> {
    if (withDefaultValue) return Collections.emptyList()

    val occurrenceRanges = occurrencesToReplace.map { it.textRange }
    return parametersUsages.entrySet()
        .asSequence()
        .filter {
            it.value.all { paramUsage ->
                occurrenceRanges.any { occurrenceRange -> occurrenceRange.contains(paramUsage.textRange) }
            }
        }
        .map { it.key }
        .toList()
}
