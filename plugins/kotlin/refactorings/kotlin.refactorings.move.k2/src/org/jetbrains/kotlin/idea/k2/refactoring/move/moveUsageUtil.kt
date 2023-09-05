// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal val KtDeclarationContainer.declarationsForUsageSearch: List<KtNamedDeclaration> get() {
    val declarationsToSearch = mutableListOf<KtNamedDeclaration>()
    if (this is KtNamedDeclaration) declarationsToSearch.add(this)
    declarations.forEach { decl ->
        if (decl is KtDeclarationContainer) {
            declarationsToSearch.addAll(decl.declarationsForUsageSearch)
        }
        else if (decl is KtNamedDeclaration) {
            declarationsToSearch.add(decl)
        }
    }
    return declarationsToSearch
}

internal fun Collection<KtNamedDeclaration>.findNonCodeUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    pkgName: FqName
): List<UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    forEach { element ->
        val newName = FqName("${pkgName.asString()}.${element.name}")
        TextOccurrencesUtil.findNonCodeUsages(
            element,
            element.resolveScope,
            element.fqName?.asString(),
            searchInCommentsAndStrings,
            searchForText,
            newName.asString(),
            usages
        )
    }
    return usages
}

internal fun retargetUsagesAfterMove(usages: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
    for (usageInfo in usages.filterIsInstance<K2MoveRenameUsageInfo>()) {
        usageInfo.retarget(usageInfo.referencedElement)
    }
    val project = oldToNewMap.values.firstOrNull()?.project ?: return
    RenameUtil.renameNonCodeUsages(project, usages.filterIsInstance<NonCodeUsageInfo>().toTypedArray())
}