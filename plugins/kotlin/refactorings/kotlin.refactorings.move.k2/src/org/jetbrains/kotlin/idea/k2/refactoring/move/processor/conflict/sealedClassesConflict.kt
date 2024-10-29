// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.projectStructure.productionOrTestSourceModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willBeMoved
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * Returns the direct inheritors of the sealed class.
 * Returns an empty list if the class is not sealed.
 */
internal fun KtClass.getDirectSealedInheritors(): List<KtClassOrObject> {
    if (!isSealed()) return emptyList()

    return DirectKotlinClassInheritorsSearch.search(this).filterIsInstance<KtClassOrObject>()
}

/**
 * Returns all _direct_ super types of this class that are sealed.
 */
internal fun KtClassOrObject.getSealedSuperTypes(): Set<KtClass> {
    return analyze(this) {
        val directSuperTypes = (symbol as? KaClassSymbol)?.superTypes ?: return emptySet()
        directSuperTypes.mapNotNullTo(mutableSetOf()) { superType ->
            val superClass = superType.symbol?.psi as? KtClass ?: return@mapNotNullTo null
            superClass.takeIf { it.isSealed() }
        }
    }
}

// Makes sure the list is sorted to ensure consistent results
private fun List<KtClassOrObject>.toListOfNames() = map {
    it.fqName?.asString() ?: it.nameAsSafeName.asString()
}.sorted()

/**
 * Checks for conflicts within moved sealed classes or their inheritors.
 * We can have a conflict if:
 *  - A sealed class is moved to a different package or module, but some of its children are not
 *  - An inheritor of a sealed class is moved to a different package or module, but its parent is not
 */
@OptIn(KaExperimentalApi::class)
internal fun checkSealedClassesConflict(
    declarationsToMove: Iterable<KtNamedDeclaration>,
    targetPackage: FqName,
    targetKaModule: KaModule,
    targetIdeaModule: Module
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()

    val classesToMove = declarationsToMove.filterIsInstance<KtClassOrObject>()
    if (classesToMove.isEmpty()) return conflicts

    // First, we collect all the sealed class hierarchies that have been affected
    val allAffectedSealedSuperTypes = mutableSetOf<KtClass>()
    for (classToMove in classesToMove) {
        val classModule = classToMove.module?.productionOrTestSourceModuleInfo?.toKaModule()
        // If we stay in the same package AND the module is the same as before, sealed classes can be moved safely
        if (classModule == targetKaModule && targetPackage == classToMove.containingKtFile.packageFqName) continue

        // The sealed class itself is being moved
        if (classToMove is KtClass && classToMove.isSealed()) {
            allAffectedSealedSuperTypes.add(classToMove)
        }

        // An inheritor of a sealed class is being moved
        allAffectedSealedSuperTypes.addAll(classToMove.getSealedSuperTypes())
    }

    // Nothing needs to be checked. No sealed classes or their inheritors were moved.
    if (allAffectedSealedSuperTypes.isEmpty()) return conflicts

    val movedClasses = declarationsToMove.filterIsInstance<KtClassOrObject>().toSet()
    for (sealedSuperType in allAffectedSealedSuperTypes) {
        val directInheritors = sealedSuperType.getDirectSealedInheritors()

        // The entire hierarchy is being moved and will remain intact
        if (sealedSuperType.willBeMoved(movedClasses) && directInheritors.all { it.willBeMoved(movedClasses) }) continue

        if (sealedSuperType in movedClasses) {
            // Our hierarchy is being moved, but some of its inheritors are being left behind
            val remainingInheritors = directInheritors.filter { it !in movedClasses }
            conflicts.putValue(
                sealedSuperType, KotlinBundle.message(
                    "text.sealed.broken.hierarchy.still.in.source",
                    sealedSuperType.nameAsSafeName.asString(),
                    sealedSuperType.containingKtFile.packageFqName.asString(),
                    sealedSuperType.module?.name ?: "",
                    remainingInheritors.toListOfNames()
                )
            )
        } else {
            // Some inheritors are being moved away from their sealed parent
            val movedInheritors = directInheritors.filter { it in movedClasses }
            val allHierarchyMembers = buildList {
                add(sealedSuperType)
                addAll(directInheritors)
            }

            for (movedInheritor in movedInheritors) {
                conflicts.putValue(
                    movedInheritor, KotlinBundle.message(
                        "text.sealed.broken.hierarchy.none.in.target",
                        sealedSuperType.nameAsSafeName.asString(),
                        targetPackage.asString(),
                        targetIdeaModule.name,
                        allHierarchyMembers.toListOfNames()
                    )
                )
            }
        }
    }

    return conflicts
}