// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

internal val KtDeclarationContainer.declarationsForUsageSearch: List<KtNamedDeclaration> get() {
    val declarationsToSearch = mutableListOf<KtNamedDeclaration>()
    if (this is KtNamedDeclaration && needsReferenceUpdate) declarationsToSearch.add(this)
    declarations.forEach { decl ->
        if (decl is KtDeclarationContainer) {
            declarationsToSearch.addAll(decl.declarationsForUsageSearch)
        }
        else if (decl is KtNamedDeclaration && decl.needsReferenceUpdate) {
            declarationsToSearch.add(decl)
        }
    }
    return declarationsToSearch
}

internal val KtNamedDeclaration.needsReferenceUpdate: Boolean
    get() {
        return when (this) {
            is KtFunction -> !isLocal && !isInstanceAccessible
            is KtProperty -> !isLocal && !isInstanceAccessible
            is KtClassOrObject -> true
            else -> false
        }
    }

private val KtNamedDeclaration.isInstanceAccessible get() = parent.parent is KtClass

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
        usageInfo.referencedElement?.let { usageInfo.retarget(it) }
    }
    val project = oldToNewMap.values.firstOrNull()?.project ?: return
    RenameUtil.renameNonCodeUsages(project, usages.filterIsInstance<NonCodeUsageInfo>().toTypedArray())
}