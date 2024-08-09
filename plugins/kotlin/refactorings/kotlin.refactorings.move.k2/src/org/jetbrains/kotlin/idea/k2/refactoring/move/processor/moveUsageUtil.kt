// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.markInternalUsages
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * Retrieves all declarations that might need their references to be updated.
 * This excludes, for example, instance and local functions and properties.
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
internal val KtDeclarationContainer.allDeclarationsToUpdate: List<KtNamedDeclaration>
    get() {
        val declarationsToSearch = mutableListOf<KtNamedDeclaration>()
        if (this is KtNamedDeclaration && needsReferenceUpdate) declarationsToSearch.add(this)
        declarations.forEach { decl ->
            if (decl is KtDeclarationContainer) {
                declarationsToSearch.addAll(decl.allDeclarationsToUpdate)
            } else if (decl is KtNamedDeclaration && decl.needsReferenceUpdate) {
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
internal val KtDeclarationContainer.topLevelDeclarationsToUpdate: List<KtNamedDeclaration>
    get() {
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
            is KtClassLikeDeclaration -> true
            else -> false
        }
    }

internal fun KtFile.findUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    newPkgName: FqName
): List<UsageInfo> {
    markInternalUsages(this, this)
    return topLevelDeclarationsToUpdate.flatMap { decl ->
        K2MoveRenameUsageInfo.findExternalUsages(decl) + decl.findNonCodeUsages(searchInCommentsAndStrings, searchForText, newPkgName)
    }
}

/**
 * Finds usages to a [KtNamedDeclaration] that might need to be updated for the move refactoring, this includes non-code and internal
 * usages.
 * Internal usages are marked by [K2MoveRenameUsageInfo.internalUsageInfo].
 * @return external usages of the declaration to move
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
internal fun retargetUsagesAfterMove(usages: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {
    K2MoveRenameUsageInfo.retargetUsages(usages.filterIsInstance<K2MoveRenameUsageInfo>(), oldToNewMap)
    val project = oldToNewMap.values.firstOrNull()?.project ?: return
    RenameUtil.renameNonCodeUsages(project, usages.filterIsInstance<NonCodeUsageInfo>().toTypedArray())
}

internal fun <T : MoveRenameUsageInfo> List<T>.groupByFile(): Map<PsiFile, List<T>> = groupBy {
    it.element?.containingFile ?: error("Could not find containing file")
}.toSortedMap(object : Comparator<PsiFile> {
    // Use a sorted map to get consistent results by the refactoring
    // This is done to reduce flakiness and make the results reproducible
    override fun compare(o1: PsiFile?, o2: PsiFile?): Int {
        return o1?.virtualFile?.path?.compareTo(o2?.virtualFile?.path ?: return -1) ?: -1
    }
})

internal fun <T : MoveRenameUsageInfo> Map<PsiFile, List<T>>.sortedByOffset(): Map<PsiFile, List<T>> = mapValues { (_, value) ->
    value.sortedBy { it.element?.textOffset }
}