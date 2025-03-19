// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.refactoring.replaceListPsiAndKeepDelimiters
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.utils.ifEmpty

internal class KotlinComponentUsageInDestructuring(element: KtDestructuringDeclarationEntry) :
  UsageInfo(element), KotlinBaseChangeSignatureUsage {
    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement? {
        if (!changeInfo.isParameterSetOrOrderChanged) return null

        val declaration = element.parent as KtDestructuringDeclaration
        val currentEntries = declaration.entries
        val newParameterInfos = changeInfo.newParameters.filter { it != changeInfo.receiverParameterInfo }

        val ktCallableDeclaration = changeInfo.method as KtCallableDeclaration
        val newDestructuring = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(ktCallableDeclaration) {
                    KtPsiFactory(element.project).buildDestructuringDeclaration {
                        val lastIndex = newParameterInfos.indexOfLast { it.oldIndex in currentEntries.indices }
                        val nameValidator = KotlinDeclarationNameValidator(
                          ktCallableDeclaration,
                          true,
                          KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                        )
                        appendFixedText("val (")
                        for (i in 0..lastIndex) {
                            if (i > 0) {
                                appendFixedText(", ")
                            }

                            val paramInfo = newParameterInfos[i]
                            val oldIndex = paramInfo.oldIndex
                            if (oldIndex >= 0 && oldIndex < currentEntries.size) {
                                appendChildRange(PsiChildRange.singleElement(currentEntries[oldIndex]))
                            } else {
                                appendFixedText(KotlinNameSuggester.suggestNameByName(paramInfo.name) { nameValidator.validate(it) })
                            }
                        }
                        appendFixedText(")")
                    }
                }
            }
        }
        replaceListPsiAndKeepDelimiters(
            changeInfo,
            declaration,
            newDestructuring,
            {
                apply {
                    val oldEntries = entries.ifEmpty { return@apply }
                    val firstOldEntry = oldEntries.first()
                    val lastOldEntry = oldEntries.last()
                    val newEntries = it.entries
                    if (newEntries.isNotEmpty()) {
                        addRangeBefore(newEntries.first(), newEntries.last(), firstOldEntry)
                    }
                    deleteChildRange(firstOldEntry, lastOldEntry)
                }
            },
            { entries }
        )
        return null
    }
}