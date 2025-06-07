// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.markInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.OuterInstanceReferenceUsageInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

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
    moveTarget: K2MoveTargetDescriptor
): List<UsageInfo> {
    return K2MoveRenameUsageInfo.find(this) + findNonCodeUsages(searchInCommentsAndStrings, searchForText, moveTarget.pkgName, moveTarget)
}

private fun K2MoveTargetDescriptor.File.getJavaFileFacadeFqName(): FqName {
    return pkgName.child(Name.identifier(fileName.substringBeforeLast(".").capitalizeAsciiOnly() + "Kt"))
}

/**
 * @param newPkgName new package name to store in the usage info
 * @param moveTarget the new location for the file. Could be a file with a different name yielding different Java facade names.
 * @return non-code usages like occurrences in documentation, kdoc references (references in square brackets) are considered
 * code usages and won't be found when calling this method.
 */
@OptIn(KaExperimentalApi::class)
private fun KtNamedDeclaration.findNonCodeUsages(
    searchInCommentsAndStrings: Boolean,
    searchForText: Boolean,
    newPkgName: FqName,
    moveTarget: K2MoveTargetDescriptor? = null
): List<UsageInfo> {
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
    // Add usages of the Kotlin FqName
    name?.let { elementName ->
        fqName?.quoteIfNeeded()?.asString()?.let { currentName ->
            val newName = "${newPkgName.asString()}.$elementName"
            addNonCodeUsages(currentName, newName)
        }
    }

    val targetFileFacadeName = if (moveTarget is K2MoveTargetDescriptor.File) {
        moveTarget.getJavaFileFacadeFqName()
    } else {
        containingKtFile.javaFileFacadeFqName
    }

    fun addJavaFacadeUsages(elementName: String) {
        val currentJavaFacadeName = StringUtil.getQualifiedName(containingKtFile.javaFileFacadeFqName.asString(), elementName)
        val newJavaFacadeName = StringUtil.getQualifiedName(
            StringUtil.getQualifiedName(newPkgName.asString(), targetFileFacadeName.shortName().asString()),
            elementName
        )
        addNonCodeUsages(currentJavaFacadeName, newJavaFacadeName)
    }
    val listOfNames = listOfNotNull(name).toMutableList()

    // Properties also have the additional getter and setter methods that can be referred to
    if (this is KtProperty) {
        analyze(this) {
            listOfNames.addIfNotNull((symbol as? KaPropertySymbol)?.javaGetterName?.asString())
            listOfNames.addIfNotNull((symbol as? KaPropertySymbol)?.javaSetterName?.asString())
        }
    }

    // Add references to usages of this declaration using the Java facade names
    listOfNames.forEach(::addJavaFacadeUsages)

    return usages
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


internal fun collectOuterInstanceReferences(member: KtNamedDeclaration): List<OuterInstanceReferenceUsageInfo> {
    val result = SmartList<OuterInstanceReferenceUsageInfo>()
    traverseOuterInstanceReferences(member) { result += it }
    return result
}

internal fun KtNamedDeclaration.usesOuterInstanceParameter(): Boolean {
    if (containingClassOrObject is KtObjectDeclaration) return false
    return collectOuterInstanceReferences(this).isNotEmpty()
}

context(KaSession)
private fun KaSymbol.isStrictAncestorOf(other: KaSymbol): Boolean {
    var containingDeclaration = other.containingDeclaration
    while (containingDeclaration != null) {
        if (this == containingDeclaration)  return true
        containingDeclaration = containingDeclaration.containingDeclaration
    }
    return false
}

private fun traverseOuterInstanceReferences(
    member: KtNamedDeclaration,
    body: (OuterInstanceReferenceUsageInfo) -> Unit
): Boolean {
    if (member is KtObjectDeclaration || member is KtClass && !member.isInner()) return false
    analyze(member) {
        val containingClassOrObject = member.containingClassOrObject ?: return false
        val outerClassSymbol = containingClassOrObject.symbol as? KaClassSymbol ?: return false
        val outerClassPsi = outerClassSymbol.psi
        var found = false
        member.accept(object : PsiRecursiveElementWalkingVisitor() {
            private fun getOuterInstanceReference(element: PsiElement): OuterInstanceReferenceUsageInfo? {
                return when (element) {
                    is KtThisExpression -> {
                        val symbol = element.expressionType?.symbol ?: return null
                        val symbolPsi = symbol.psi
                        val isIndirect = when {
                            symbol == outerClassSymbol -> false
                            symbolPsi != null && outerClassPsi != null && symbolPsi.isAncestor(outerClassPsi) -> true
                            else -> return null
                        }
                        OuterInstanceReferenceUsageInfo.ExplicitThis(element, member, isIndirect)
                    }

                    is KtSimpleNameExpression -> {
                        val resolvedCall = element.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return null
                        val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver as? KaImplicitReceiverValue
                        val extensionReceiver = resolvedCall.partiallyAppliedSymbol.extensionReceiver as? KaImplicitReceiverValue
                        var isIndirect = false
                        val isDoubleReceiver = when (outerClassSymbol) {
                            dispatchReceiver?.symbol -> extensionReceiver != null
                            extensionReceiver?.symbol -> dispatchReceiver != null
                            else -> {
                                isIndirect = true
                                when {
                                    dispatchReceiver?.symbol?.isStrictAncestorOf(outerClassSymbol) == true ->
                                        extensionReceiver != null

                                    extensionReceiver?.symbol?.isStrictAncestorOf(outerClassSymbol) == true ->
                                        dispatchReceiver != null

                                    else -> return null
                                }
                            }
                        }

                        val callElement = if (resolvedCall is KaVariableAccessCall) {
                            element
                        } else {
                            element.parent as? KtCallExpression
                        } ?: return null

                        OuterInstanceReferenceUsageInfo.ImplicitReceiver(callElement, member, isIndirect, isDoubleReceiver)
                    }

                    else -> null
                }
            }

            override fun visitElement(element: PsiElement) {
                getOuterInstanceReference(element)?.let {
                    body(it)
                    found = true
                    return
                }
                super.visitElement(element)
            }
        })
        return found
    }
}