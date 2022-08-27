// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class KotlinRenameUsageSearcher : RenameUsageSearcher {
    override fun collectImmediateResults(parameters: RenameUsageSearchParameters): Collection<RenameUsage> {
        val renameTarget = parameters.target as? KotlinNamedDeclarationRenameUsage

        return listOfNotNull(renameTarget)
    }

    override fun collectSearchRequests(parameters: RenameUsageSearchParameters): List<Query<out RenameUsage>> {
        val renameTarget = parameters.target as? KotlinNamedDeclarationRenameUsage ?: return emptyList()
        val symbol = PsiSymbolService.getInstance().asSymbol(renameTarget.element)

        val kotlinQuery = SearchService.getInstance()
            .searchWord(parameters.project, renameTarget.targetName)
            .inScope(parameters.searchScope)
            .inContexts(SearchContext.IN_CODE)
            .inFilesWithLanguage(KotlinLanguage.INSTANCE)
            .buildQuery(LeafOccurrenceMapper.withPointer(symbol.createPointer(), ::kotlinReferenceToRenameUsage))

        return listOf(kotlinQuery)
    }

    private fun kotlinReferenceToRenameUsage(
        expectedTarget: Symbol,
        potentialReferenceOccurence: LeafOccurrence,
    ): List<RenameUsage> {
        val referenceExpression = potentialReferenceOccurence.start.parentOfType<KtNameReferenceExpression>()
        val references = referenceExpression?.mainReference ?: return emptyList()
        val symbolReference = PsiSymbolService.getInstance().asSymbolReference(references)

        if (!symbolReference.resolvesTo(expectedTarget)) return emptyList()

        return listOf(KotlinReferenceModifiableRenameUsage(referenceExpression))
    }
}
