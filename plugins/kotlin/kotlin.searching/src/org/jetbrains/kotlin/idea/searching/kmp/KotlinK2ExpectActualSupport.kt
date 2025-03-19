// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.kmp

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.idea.search.ExpectActualSupport
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi

class KotlinK2ExpectActualSupport: ExpectActualSupport {

    override fun actualsForExpect(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> {
        if (declaration is KtParameter) {
            val function = declaration.ownerFunction as? KtCallableDeclaration ?: return emptySet()
            val index = function.valueParameters.indexOf(declaration)
            return actualsForExpect(function, module).mapNotNull { (it as? KtCallableDeclaration)?.valueParameters?.getOrNull(index) }.toSet()
        }
        return declaration.findAllActualForExpect( runReadAction { module?.moduleWithDependentsScope ?: declaration.useScope } ).mapNotNull { it.element }.toSet()
    }

    @OptIn(KaExperimentalApi::class)
    override fun expectDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? {
        if (declaration.isExpectDeclaration()) return declaration
        if (!declaration.isEffectivelyActual()) return null
        return analyze(declaration) {
            val symbol: KaDeclarationSymbol = declaration.symbol
            (symbol.getExpectsForActual().mapNotNull { (it.psi as? KtDeclaration) }).firstOrNull()
        }
    }
}