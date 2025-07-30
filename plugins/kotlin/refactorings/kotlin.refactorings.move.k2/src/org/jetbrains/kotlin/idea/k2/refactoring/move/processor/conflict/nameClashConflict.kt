// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.SymbolData.*
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.TypeData.*
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.renderForConflict
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration

private sealed interface TypeData {
    object AnyOrTypeParameter : TypeData
    class ClassType(val classId: ClassId, val typeArguments: List<TypeData>) : TypeData
    object UnknownType : TypeData
}

private sealed interface SymbolData {
    val name: Name?
    val containingSymbolFqName: FqName?

    class PropertySymbolData(
        override val name: Name?,
        override val containingSymbolFqName: FqName?
    ) : SymbolData

    class FunctionSymbolData(
        override val name: Name?,
        val valueParameters: List<TypeData>,
        override val containingSymbolFqName: FqName?
    ) : SymbolData

    class ClassSymbolData(
        override val name: Name?,
        override val containingSymbolFqName: FqName?
    ) : SymbolData

    object UnknownSignature : SymbolData {
        override val name: Name? = null
        override val containingSymbolFqName: FqName? = null
    }
}

context(_: KaSession)
private fun KaType.toTypeInfo(): TypeData {
    if (isAnyOrTypeParameter()) {
        return AnyOrTypeParameter
    }
    return when (this) {
        is KaClassType -> ClassType(
            classId = classId,
            typeArguments = typeArguments.map { it.type?.toTypeInfo() ?: UnknownType }
        )

        else -> UnknownType
    }
}

context(_: KaSession)
private fun KaSymbol.getContainingSymbolFqn(): FqName? {
    // This breaks if certain modules are used: KT-68810
    runCatching {
        containingDeclaration?.importableFqName?.let { return it }
    }
    return psi?.kotlinFqName?.parent()
}

context(_: KaSession)
private fun KaType.isAnyOrTypeParameter(): Boolean {
    if (isAnyType) return true
    return this is KaTypeParameterType
}

context(_: KaSession)
private fun KaSymbol.toSignatureData(): SymbolData {
    val containingSymbolFqName = getContainingSymbolFqn()
    return when (this) {
        is KaClassSymbol -> ClassSymbolData(name, containingSymbolFqName)
        is KaFunctionSymbol -> FunctionSymbolData(name, valueParameters.map { it.returnType.toTypeInfo() }, containingSymbolFqName)
        is KaPropertySymbol -> PropertySymbolData(name, containingSymbolFqName)
        else -> UnknownSignature
    }
}

/**
 * This conflict checker checks for conflicts arising from declarations with the same name already existing in the [targetPkg].
 * Because we are moving declarations between different useSiteModules, we cannot compare the symbols in the same analysis session.
 * Instead, we first create a representation of the symbol using [SymbolData] and [TypeData] that we then compare with
 * the existing symbols within the target scope.
 */
@OptIn(KaExperimentalApi::class)
internal fun checkNameClashConflicts(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    targetPkg: FqName,
    targetKaModule: KaModule,
    targetContainer: KtClassOrObject? = null,
): MultiMap<PsiElement, String> {

    // Note: This does not detect 100% of conflicts currently
    fun KaSession.equivalent(a: KaType, b: TypeData): Boolean {
        return when {
            // Possible type erasure conflict
            a.isAnyOrTypeParameter() && b is AnyOrTypeParameter ->         // a = T(?) | Any(?), b = T(?) | Any(?)
                true                                                    // => 100% clash
            a is KaClassType && b is ClassType -> {
                if (a.classId != b.classId || a.typeArguments.size != b.typeArguments.size) return false
                a.typeArguments.zip(b.typeArguments).all {
                    val type = it.first.type ?: return@all true
                    equivalent(type, it.second)
                }
            }
            else -> return false
        }
    }

    fun KaSession.equivalent(a: KaSymbol, b: SymbolData): Boolean = when (a) {
        is KaDeclarationSymbol -> when (a) {
            is KaFunctionSymbol -> b is FunctionSymbolData && a.name == b.name &&
                    a.valueParameters.size == b.valueParameters.size &&
                    a.valueParameters.zip(b.valueParameters).all { equivalent(it.first.returnType, it.second) }

            else -> a.name == b.name
        }

        else -> false
    }

    fun KaSession.walkDeclarations(
        currentScope: KaSymbol,
        signatureToCheck: SymbolData,
        report: (KaDeclarationSymbol, KaSymbol) -> Unit
    ) {
        val declarationName = signatureToCheck.name
        when (currentScope) {
            is KaPackageSymbol -> {
                if (signatureToCheck.containingSymbolFqName == currentScope.fqName) {
                    return
                }
                currentScope
                    .packageScope
                    .declarations
                    .filter { it.name == declarationName }
                    .filter { equivalent(it, signatureToCheck) }
                    .forEach { report(it, currentScope) }
                return
            }

            is KaClassSymbol -> {
                if (signatureToCheck.containingSymbolFqName != currentScope.classId?.packageFqName) {
                    currentScope.getSymbolContainingMemberDeclarations()
                        ?.memberScope
                        ?.declarations
                        ?.filter { it.name == declarationName }
                        ?.filter { equivalent(it, signatureToCheck) }
                        ?.forEach { report(it, currentScope) }
                }
                return
            }

        }
        currentScope.containingDeclaration?.let {  walkDeclarations(it, signatureToCheck, report) }
    }

    val conflicts = MultiMap<PsiElement, String>()
    allDeclarationsToMove
        .forEach { declaration ->
            val signatureData = analyze(declaration) {
                declaration.symbol.toSignatureData()
            }
            analyze(targetKaModule) {
                if (!declaration.canBeAnalysed()) return@analyze

                val containerSymbol = if (targetContainer is KtClassOrObject) {
                    targetContainer.symbol
                } else findPackage(targetPkg)

                if (containerSymbol == null) return@analyze
                walkDeclarations(containerSymbol, signatureData) { conflictingSymbol, conflictingScope ->
                    val renderedDeclaration = analyze(declaration) {
                        // This needs to be in a nested analyze call because the KaModules might
                        // differ between source and target
                        declaration.symbol.renderForConflict()
                    }
                    val message = KotlinBundle.message(
                        "text.declarations.clash.move.0.destination.1.declared.in.scope.2",
                        renderedDeclaration,
                        conflictingSymbol.renderForConflict(),
                        conflictingScope.renderForConflict().ifBlank { "default" },
                    )
                    conflicts.putValue(declaration, message)
                }
            }
        }
    return conflicts
}