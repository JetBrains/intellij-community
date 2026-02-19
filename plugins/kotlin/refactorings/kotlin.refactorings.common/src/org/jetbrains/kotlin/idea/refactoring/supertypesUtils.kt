// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
@ApiStatus.Internal
fun KtClassLikeDeclaration.resolveDirectSupertypes(): Set<PsiElement> {
    return allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(this) {
                val targetClassSymbol = symbol as? KaClassifierSymbol ?: return@analyze emptySet()

                targetClassSymbol.defaultType
                    .directSupertypes
                    .mapNotNull { type -> type.symbol?.psi }
                    .toSet()
            }
        }
    }
}

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
@ApiStatus.Internal
fun PsiClass.resolveDirectSupertypes(): Set<PsiElement> {
    val kaModule = getKaModule(project, useSiteModule = null)

    return allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(kaModule) {
                val targetClassSymbol = namedClassSymbol ?: return@analyze emptySet()

                targetClassSymbol.defaultType
                    .directSupertypes
                    .mapNotNull { type -> type.symbol?.psi }
                    .toSet()
            }
        }
    }
}

@ApiStatus.Internal
fun PsiNamedElement.resolveDirectSupertypes(): Set<PsiElement> = when (this) {
    is KtClassLikeDeclaration -> resolveDirectSupertypes()
    is PsiClass -> resolveDirectSupertypes()
    else -> emptySet()
}

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
@ApiStatus.Internal
fun KtClassLikeDeclaration.resolveAllSupertypes(): Set<PsiElement> {
    allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(this) {
                val targetClassSymbol = symbol as? KaClassifierSymbol ?: return emptySet()

                return targetClassSymbol.defaultType
                    .allSupertypes
                    .mapNotNull { type -> type.symbol?.psi }
                    .toSet()
            }
        }
    }
}

