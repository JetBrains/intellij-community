// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModuleOfTypeSafe
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleContainingElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.tryFindConflict
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.internalUsageElements
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willNotBeMoved
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.refactoring.pullUp.willBeMoved
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

private fun PsiElement.createVisibilityConflict(referencedDeclaration: PsiElement): Pair<PsiElement, String> {
    return this to KotlinBundle.message(
        "text.0.uses.1.which.will.be.inaccessible.after.move",
        RefactoringUIUtil.getDescription(getContainer(), false),
        RefactoringUIUtil.getDescription(referencedDeclaration, false)
    ).capitalizeAsciiOnly()
}

/**
 * If visibility isn't there before the refactoring starts, we don't report it as a conflict.
 */
private fun MoveRenameUsageInfo.isVisibleBeforeMove(): Boolean {
    return try {
        val declaration = upToDateReferencedElement as? PsiNamedElement ?: return false
        declaration.isVisibleTo(element ?: return false)
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        fileLogger().error(e)
        return true
    }
}

@ApiStatus.Internal
fun PsiNamedElement.isVisibleTo(usage: PsiElement): Boolean {
    return if (usage is KtElement) {
        analyze(usage) { isVisibleTo(usage) }
    } else {
        lightIsVisibleTo(usage)
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
private fun PsiNamedElement.isVisibleTo(usage: KtElement): Boolean {
    val file = (usage.containingFile as? KtFile)?.symbol ?: return false
    val symbol = if (this is KtNamedDeclaration) {
        symbol
    } else {
        if (this !is PsiMember) return false // get Java symbol through resolve because it is not possible through getSymbol
        usage.mainReference?.resolveToSymbol() as? KaDeclarationSymbol ?: return false
    }
    return createUseSiteVisibilityChecker(file, receiverExpression = null, usage).isVisible(symbol)
}

private fun PsiNamedElement.lightIsVisibleTo(usage: PsiElement): Boolean {
    val declarations = if (this is KtNamedDeclaration) toLightElements() else listOf(this)
    return declarations.all { lightDecl ->
        if (lightDecl !is PsiMember) return@all false
        JavaResolveUtil.isAccessible(lightDecl, lightDecl.containingClass, lightDecl.modifierList, usage, null, null)
    }
}

private fun KaModule.isFriendDependencyFor(other: KaModule): Boolean {
    return when (this) {
        in other.directFriendDependencies,
        in other.transitiveDependsOnDependencies -> true

        else -> false
    }
}

private fun isPrivateVisibleAt(referencingElement: PsiElement, target: K2MoveTargetDescriptor.Declaration<*>): Boolean {
    return when (target) {
        is K2MoveTargetDescriptor.File -> {
            // For top level declarations, private members will be visible within the entire file
            referencingElement.containingFile == target.getTarget()
        }
        is K2MoveTargetDescriptor.ClassBody<*> -> {
            val targetClass = target.getTarget() ?: return false
            // Companion objects can access private members in its parent and vice versa
            val visibilityTarget = if (targetClass is KtObjectDeclaration && targetClass.isCompanion()) {
                targetClass.containingClass() ?: targetClass
            } else {
                targetClass
            }
            // Private members are visible anywhere inside its hierarchy
            visibilityTarget.isAncestor(referencingElement)
        }
    }
}

/**
 * Check whether the moved external usages are still visible towards their non-physical declaration.
 */
internal fun checkVisibilityConflictForNonMovedUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>,
    targetDir: PsiDirectory,
    target: K2MoveTargetDescriptor.Declaration<*>? = null
): MultiMap<PsiElement, String> {
    return usages
        .filter { usageInfo -> usageInfo.willNotBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { usageInfo ->
            tryFindConflict {
                val usageElement = usageInfo.element ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                analyze(referencedDeclaration) {
                    val usageKaModule = usageElement.module?.toKaSourceModuleContainingElement(usageElement)
                    val referencedDeclarationKaModule = targetDir.module?.toKaSourceModuleContainingElement(targetDir)
                    val isVisible = when (referencedDeclaration.symbol.visibility) {
                        KaSymbolVisibility.PRIVATE -> {
                            target != null && isPrivateVisibleAt(referencedDeclaration, target)
                        }

                        KaSymbolVisibility.INTERNAL -> usageElement.module == targetDir.module ||
                                (referencedDeclarationKaModule != null && usageKaModule != null &&
                                        referencedDeclarationKaModule.isFriendDependencyFor(usageKaModule))

                        else -> true
                    }
                    if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
                }
            }
        }
        .toMultiMap()
}

/**
 * Returns the first parent class/object symbol containing this [KaSymbol].
 * Note: This function is strict and also returns a strict parent if the given symbol is a class.
 */
context(_: KaSession)
private fun KaSymbol.containingClassSymbol(): KaClassSymbol? {
    // TODO: Needs to be adapted when moving into classes is supported
    val containingSymbol = containingSymbol
    if (containingSymbol is KaClassSymbol) return containingSymbol
    else return containingSymbol?.containingClassSymbol()
}

/**
 * Returns true if the [refererSymbol] is contained within a class that inherits from the given class symbol.
 */
context(_: KaSession)
private fun KaClassSymbol.isSuperClassForParentOf(
    refererSymbol: KaSymbol,
): Boolean {
    // TODO: Support moving into other classes, then the checks need to be expanded
    if (refererSymbol is KaClassSymbol && refererSymbol.isSubClassOf(this)) return true
    return refererSymbol.containingSymbol?.let { isSuperClassForParentOf(it) } == true
}

context(_: KaSession)
private fun KaSymbol.isProtectedVisibleFrom(refererSymbol: KaSymbol): Boolean {
    // For protected visibility to work, we need to be within a class that inherits from
    // the parent class of the referred symbol.
    return containingClassSymbol()?.isSuperClassForParentOf(refererSymbol) == true
}

/**
 * Check whether the moved internal usages are still visible towards their physical declaration.
 */
fun checkVisibilityConflictsForInternalUsages(
    topLevelDeclarationsToMove: Collection<KtNamedDeclaration>,
    allDeclarationsToMove: Collection<KtNamedDeclaration>,
    targetPkg: FqName,
    targetDir: PsiDirectory,
    target: K2MoveTargetDescriptor.Declaration<*>? = null
): MultiMap<PsiElement, String> {
    val usageKaModule = targetDir.module?.toKaSourceModuleContainingElement(targetDir)

    return topLevelDeclarationsToMove
        .flatMap { it.internalUsageElements() }
        .mapNotNull { refExpr -> (refExpr.internalUsageInfo ?: return@mapNotNull null) }
        .filter { usageInfo -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { usageInfo ->
            tryFindConflict {
                val usageElement = usageInfo.element as? KtElement ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@tryFindConflict null
                val isVisible = if (referencedDeclaration is KtNamedDeclaration) {
                    analyze(usageElement) {
                        val referencedDeclarationKaModule = referencedDeclaration.getKaModuleOfTypeSafe<KaSourceModule>(
                            referencedDeclaration.project,
                            useSiteModule = null,
                        )
                        val symbol = referencedDeclaration.symbol
                        val visibility = if (symbol is KaConstructorSymbol) {
                            (symbol.containingSymbol as? KaClassSymbol)?.let { classSymbol ->
                                symbol.visibility.coerceAtLeast(classSymbol.visibility)
                            }
                        } else {
                            symbol.visibility
                        }
                        when (visibility) {
                            KaSymbolVisibility.PRIVATE -> target != null && isPrivateVisibleAt(usageElement, target)
                            KaSymbolVisibility.INTERNAL -> referencedDeclaration.module == targetDir.module ||
                                    (referencedDeclarationKaModule != null && usageKaModule != null &&
                                            referencedDeclarationKaModule.isFriendDependencyFor(usageKaModule))

                            KaSymbolVisibility.PROTECTED -> {
                                val refererSymbol = usageElement.getStrictParentOfType<KtNamedDeclaration>()?.symbol
                                if (refererSymbol != null) {
                                    referencedDeclaration.symbol.isProtectedVisibleFrom(refererSymbol)
                                } else true
                            }

                            else -> true
                        }
                    }
                } else if (referencedDeclaration is PsiModifierListOwner) {
                    val modifierList = referencedDeclaration.modifierList ?: return@tryFindConflict null
                    val accessLevel = PsiUtil.getAccessLevel(modifierList)
                    when (accessLevel) {
                        PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL -> {
                            if (referencedDeclaration is PsiMethod && referencedDeclaration.isConstructor) {
                                true // if a constructor is protected, it's accessible outside the package
                            } else {
                                val declFqn = (referencedDeclaration as PsiModifierListOwner).containingFile.getFqNameByDirectory()
                                declFqn == targetPkg
                                if (declFqn == targetPkg) true
                                else if (accessLevel == PsiUtil.ACCESS_LEVEL_PROTECTED) {
                                    analyze(usageElement) {
                                        // For Java, we still use the analysis API to check for protected visibility
                                        // to reuse the same code as for Kotlin.
                                        val refererSymbol =
                                            usageElement.getStrictParentOfType<KtNamedDeclaration>()?.symbol ?: return@analyze false
                                        val referencedSymbol = (usageElement as? KtReferenceExpression)?.mainReference?.resolveToSymbol()
                                            ?: usageElement.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
                                            ?: return@analyze false
                                        referencedSymbol.isProtectedVisibleFrom(refererSymbol)
                                    }
                                } else {
                                    false
                                }
                            }
                        }

                        else -> true
                    }
                } else true
                if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
            }
        }.toMultiMap()
}