// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.kmp

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.search.ExpectActualSupport
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import com.intellij.openapi.application.runReadAction

class KotlinK2ExpectActualSupport: ExpectActualSupport {

    override fun actualsForExpected(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> {
        if (declaration is KtParameter) {
            val function = declaration.ownerFunction as? KtCallableDeclaration ?: return emptySet()
            val index = function.valueParameters.indexOf(declaration)
            return actualsForExpected(function, module).mapNotNull { (it as? KtCallableDeclaration)?.valueParameters?.getOrNull(index) }.toSet()
        }
        return declaration.findAllActualForExpect( runReadAction { module?.moduleTestsWithDependentsScope ?: declaration.useScope } ).mapNotNull { it.element }.toSet()
    }

    override fun expectedDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? {
        if (declaration.isExpectDeclaration()) return declaration
        if (!declaration.isEffectivelyActual()) return null
        return analyze(declaration) {
            val symbol: KtDeclarationSymbol = declaration.getSymbol()
            (symbol.getExpectsForActual().mapNotNull { (it.psi as? KtDeclaration) }).firstOrNull()
        }
    }
}