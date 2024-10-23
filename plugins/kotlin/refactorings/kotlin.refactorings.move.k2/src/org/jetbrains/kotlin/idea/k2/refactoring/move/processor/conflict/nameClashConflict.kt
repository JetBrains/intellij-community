// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.renderForConflict
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * This conflict checker checks for conflicts arising from declarations with the same name already existing in the [targetPkg].
 */
@OptIn(KaExperimentalApi::class)
fun checkNameClashConflicts(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    targetPkg: FqName,
    targetKaModule: KaModule,
): MultiMap<PsiElement, String> {
    fun KaSession.isAnyOrTypeParameter(type: KaType): Boolean {
        if (type.isAnyType) return true
        return type is KaTypeParameterType
    }

    fun KaSession.equivalent(a: KaType, b: KaType): Boolean {
        val aSupertypes = a.allSupertypes.toList()
        val bSupertypes = b.allSupertypes.toList()
        return when {
            // Possible type erasure conflict
            isAnyOrTypeParameter(a) && isAnyOrTypeParameter(b) ->         // a = T(?) | Any(?), b = T(?) | Any(?)
                true                                                    // => 100% clash
            aSupertypes.size == 1 && bSupertypes.size == 1 ->           // a = T: T1, b = T: T2
                equivalent(aSupertypes.first(), bSupertypes.first())    // equivalent(T1, T2) => clash
            a is KaClassType && b is KaClassType &&
                    a.typeArguments.isNotEmpty() && b.typeArguments.isNotEmpty() -> // a = Something<....>, b = SomethingElse<....>
                a.classId.shortClassName == b.classId.shortClassName  // equivalent(Something, SomethingElse) => clash
            else -> a.semanticallyEquals(b)
        }
    }

    fun KaSession.equivalent(a: KaSymbol, b: KaSymbol): Boolean = when (a) {
        is KaDeclarationSymbol -> when (a) {
            is KaFunctionSymbol -> b is KaFunctionSymbol && a.name == b.name &&
                    a.valueParameters.size == b.valueParameters.size &&
                    a.valueParameters.zip(b.valueParameters).all { equivalent(it.first, it.second) }

            is KaValueParameterSymbol -> b is KaValueParameterSymbol
                    && equivalent(a.returnType, b.returnType)

            else -> b is KaDeclarationSymbol && a.name == b.name
        }

        else -> false
    }

    fun KaSession.getContainingSymbolFqn(symbol: KaSymbol): FqName? {
        // This breaks if certain modules are used: KT-68810
        runCatching {
            symbol.containingDeclaration?.importableFqName?.let { return it }
        }
        return symbol.psi?.kotlinFqName?.parent()
    }

    fun KaSession.walkDeclarations(
        currentScope: KaSymbol,
        declaration: KaDeclarationSymbol,
        report: (KaDeclarationSymbol, KaSymbol) -> Unit
    ) {
        val declarationName = declaration.name ?: return
        when (currentScope) {
            is KaPackageSymbol -> {
                if (getContainingSymbolFqn(declaration) == currentScope.fqName) {
                    return
                }
                currentScope
                    .packageScope
                    .declarations
                    .filter { it.name == declarationName }
                    .filter { equivalent(it, declaration) }
                    .forEach { report(it, currentScope) }
                return
            }

            is KaClassSymbol -> {
                if (getContainingSymbolFqn(declaration) != currentScope.classId?.packageFqName) {
                    currentScope.getSymbolContainingMemberDeclarations()
                        ?.memberScope
                        ?.declarations
                        ?.filter { it.name == declarationName }
                        ?.filter { equivalent(it, declaration) }
                        ?.forEach { report(it, currentScope) }
                }
                return
            }

        }
        currentScope.containingDeclaration?.let { walkDeclarations(it, declaration, report) }
    }


    val conflicts = MultiMap<PsiElement, String>()
    allDeclarationsToMove
        .forEach { declaration ->
            analyze(targetKaModule) {
                val declarationSymbol = declaration.symbol
                val packageSymbol = findPackage(targetPkg) ?: return@analyze
                walkDeclarations(packageSymbol, declarationSymbol) { conflictingSymbol, conflictingScope ->
                    val message = KotlinBundle.message(
                        "text.declarations.clash.move.0.destination.1.declared.in.scope.2",
                        declarationSymbol.renderForConflict(),
                        conflictingSymbol.renderForConflict(),
                        conflictingScope.renderForConflict(),
                    )
                    conflicts.putValue(declaration, message)
                }
            }
        }
    return conflicts
}