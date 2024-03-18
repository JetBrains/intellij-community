// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * Retrieves all declarations that might need there references to be updated. This excludes for example instance and local methods and
 * properties.
 *
 * Example:
 * ```
 * class A {
 *   class B {
 *      fun foo() { }
 *   }
 *
 *   companion object {
 *      fun bar() { }
 *   }
 * }
 *
 * fun fooBar() { }
 * ```
 * Will return `A`, `B`, `A#bar` and `fooBar`
 *
 * @see topLevelDeclarationsToUpdate
 */
internal val KtDeclarationContainer.allDeclarationsToUpdate: List<KtNamedDeclaration> get() {
    val declarationsToSearch = mutableListOf<KtNamedDeclaration>()
    if (this is KtNamedDeclaration && needsReferenceUpdate) declarationsToSearch.add(this)
    declarations.forEach { decl ->
        if (decl is KtDeclarationContainer) {
            declarationsToSearch.addAll(decl.allDeclarationsToUpdate)
        }
        else if (decl is KtNamedDeclaration && decl.needsReferenceUpdate) {
            declarationsToSearch.add(decl)
        }
    }
    return declarationsToSearch
}

/**
 * Retrieves top level declarations that might need there references to be updated. This excludes for example instance and local methods and
 * properties.
 *
 * Example:
 * ```
 * class A {
 *   class B {
 *      fun foo() { }
 *   }
 *
 *   companion object {
 *      fun bar() { }
 *   }
 * }
 *
 * fun fooBar() { }
 * ```
 * Will return `A` and `fooBar`
 *
 * @see allDeclarationsToUpdate
 */
internal val KtDeclarationContainer.topLevelDeclarationsToUpdate: List<KtNamedDeclaration> get() {
    return declarations.filterIsInstance<KtNamedDeclaration>().filter(KtNamedDeclaration::needsReferenceUpdate)
}

/**
 * @return whether references to this declaration need to be updated. Instance or local methods and properties for example don't need to be
 * updated when moving.
 */
internal val KtNamedDeclaration.needsReferenceUpdate: Boolean
    get() {
        val isClassMember = parent.parent is KtClass
        return when (this) {
            is KtFunction -> !isLocal && !isClassMember
            is KtProperty -> !isLocal && !isClassMember
            is KtClassOrObject -> true
            else -> false
        }
    }

internal fun KtDeclarationContainer.findUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    newPkgName: FqName
): List<UsageInfo> {
    return topLevelDeclarationsToUpdate.flatMap { it.findUsages(searchInCommentsAndStrings, searchForText, newPkgName) }
}

/**
 * Finds usages to a [KtNamedDeclaration] that might need to be updated for the move refactoring, this includes non-code and internal
 * usages.
 */
internal fun KtNamedDeclaration.findUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    newPkgName: FqName
): List<UsageInfo> {
    return K2MoveRenameUsageInfo.find(this) + findNonCodeUsages(searchInCommentsAndStrings, searchForText, newPkgName)
}

/**
 * @param newPkgName new package name to store in the usage info
 * @return non-code usages like occurrences in documentation, kdoc references (references in square brackets) are considered
 * code usages and won't be found when calling this method.
 */
private fun KtNamedDeclaration.findNonCodeUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    newPkgName: FqName
): List<UsageInfo> {
    return name?.let { elementName ->
        val usages = mutableListOf<UsageInfo>()
        fun addNonCodeUsages(oldFqn: String, newFqn: String) {
            TextOccurrencesUtil.findNonCodeUsages(
                this,
                resolveScope,
                oldFqn,
                searchInCommentsAndStrings,
                searchForText,
                newFqn,
                usages
            )
        }

        fqName?.quoteIfNeeded()?.asString()?.let { currentName ->
            val newName = "${newPkgName.asString()}.$elementName"
            addNonCodeUsages(currentName, newName)
        }

        val currentJavaFacadeName = StringUtil.getQualifiedName(containingKtFile.javaFileFacadeFqName.asString(), elementName)
        val newJavaFacadeName = StringUtil.getQualifiedName(
            StringUtil.getQualifiedName(newPkgName.asString(), containingKtFile.javaFileFacadeFqName.shortName().asString()),
            elementName
        )
        addNonCodeUsages(currentJavaFacadeName, newJavaFacadeName)
        return usages
    } ?: emptyList()
}

/**
 * Retargets [usages] to the moved elements stored in [oldToNewMap].
 */
internal fun retargetUsagesAfterMove(usages: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
    K2MoveRenameUsageInfo.retargetUsages(usages, oldToNewMap)
    val project = oldToNewMap.values.firstOrNull()?.project ?: return
    RenameUtil.renameNonCodeUsages(project, usages.filterIsInstance<NonCodeUsageInfo>().toTypedArray())
}