// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.kmp

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

@RequiresBackgroundThread(generateAssertion = false)
fun KtDeclaration.findAllActualForExpect(searchScope: SearchScope = runReadAction { useScope }): Sequence<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    val scope = searchScope as? GlobalSearchScope ?: return emptySequence()
    val containingClassOrObjectOrSelf = parentOfType<KtClassOrObject>(withSelf = true)
    // covers cases like classes, class functions and class properties
    containingClassOrObjectOrSelf?.fqName?.let { fqName ->
        val fqNameAsString = fqName.asString()
        val targetDeclarations = KotlinFullClassNameIndex.getAllElements(fqNameAsString, project, scope) {
            it.matchesWithExpect(containingClassOrObjectOrSelf)
        } + KotlinTopLevelTypeAliasFqNameIndex.getAllElements(fqNameAsString, project, scope) {
            it.matchesWithExpect(containingClassOrObjectOrSelf)
        }

        return targetDeclarations.mapNotNull { targetDeclaration ->
            when (declaration) {
                is KtClassOrObject -> targetDeclaration
                is KtConstructor<*> -> {
                    if (targetDeclaration is KtClass) {
                        val primaryConstructor = targetDeclaration.primaryConstructor
                        if (primaryConstructor?.matchesWithExpect(declaration) == true) {
                            primaryConstructor
                        } else {
                            val secondaryConstructor = targetDeclaration.secondaryConstructors.find { it.matchesWithExpect(declaration) }
                            if (secondaryConstructor != null) secondaryConstructor else if (declaration.valueParameters.isEmpty()) targetDeclaration else null
                        }
                    } else null
                }
                is KtNamedDeclaration ->
                    when (targetDeclaration) {
                        is KtClassOrObject -> targetDeclaration.declarations.firstOrNull {
                            it is KtNamedDeclaration && it.name == declaration.name && it.matchesWithExpect(declaration)
                        }
                        else -> null
                    }

                else -> null
            }
        }.map { it.createSmartPointer() }
    }
    // top level functions
    val packageFqName = declaration.containingKtFile.packageFqName
    val name = declaration.name ?: return emptySequence()
    val topLevelFqName = packageFqName.child(Name.identifier(name)).asString()
    return when (declaration) {
        is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
            it.matchesWithExpect(declaration)
        }

        is KtProperty -> KotlinTopLevelPropertyFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
            it.matchesWithExpect(declaration)
        }

        else -> emptySequence()
    }.map { it.createSmartPointer() }
}

@OptIn(KaExperimentalApi::class)
private fun KtDeclaration.matchesWithExpect(expectDeclaration: KtDeclaration): Boolean {
    val declaration = this
    return declaration.isEffectivelyActual() && analyze(declaration) {
        val symbol: KaDeclarationSymbol = declaration.symbol
        return symbol.getExpectsForActual().any { it.psi == expectDeclaration }
    }
}
