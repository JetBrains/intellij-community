// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.idea.base.util.codeUsageScope
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter

internal class KotlinRenameUsageSearcher : RenameUsageSearcher {
    override fun collectImmediateResults(parameters: RenameUsageSearchParameters): Collection<RenameUsage> {
        val renameTarget = parameters.target as? KotlinNamedDeclarationRenameUsage

        return listOfNotNull(renameTarget)
    }

    override fun collectSearchRequests(parameters: RenameUsageSearchParameters): List<Query<out RenameUsage>> {
        val renameTarget = parameters.target as? KotlinNamedDeclarationRenameUsage ?: return emptyList()

        val codeUsageScope = getCodeUsageScopeForDeclaration(renameTarget.element)
        val searchScope = codeUsageScope.intersectWith(parameters.searchScope)

        val kotlinQuery = ReferencesSearch.search(renameTarget.element, searchScope)
            .mappingNotNull { it.element as? KtNameReferenceExpression }
            .mapping { KotlinReferenceModifiableRenameUsage(it) }

        return listOf(kotlinQuery)
    }

    private fun getCodeUsageScopeForDeclaration(declaration: KtNamedDeclaration): SearchScope {
        val adjustedDeclaration = when (declaration) {
            is KtParameter -> declaration.ownerFunction ?: declaration
            else -> declaration
        }
        return adjustedDeclaration.codeUsageScope()
    }
}
