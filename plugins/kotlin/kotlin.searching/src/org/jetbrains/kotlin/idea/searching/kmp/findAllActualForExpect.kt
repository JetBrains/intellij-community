// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.kmp

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import kotlin.collections.any
import kotlin.let

@RequiresBackgroundThread(generateAssertion = false)
fun KtDeclaration.findAllActualForExpect(searchScope: SearchScope = runReadAction { useScope }): Sequence<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    val scope = searchScope as? GlobalSearchScope ?: return emptySequence()
    val containingClassOrObjectOrSelf = parentOfType<KtClassOrObject>(withSelf = true)
    // covers cases like classes, class functions and class properties
    containingClassOrObjectOrSelf?.fqName?.let { fqName ->
        val fqNameAsString = fqName.asString()
        val targetDeclarations: List<KtDeclaration> = KotlinFullClassNameIndex.getAllElements(fqNameAsString, project, scope, filter = {
            it.matchesWithActual(containingClassOrObjectOrSelf)
        }) + KotlinTopLevelTypeAliasFqNameIndex.getAllElements(fqNameAsString, project, scope, filter = {
            it.matchesWithActual(containingClassOrObjectOrSelf)
        })

        return targetDeclarations.asSequence().mapNotNull { targetDeclaration ->
            when (declaration) {
                is KtClassOrObject -> targetDeclaration
                is KtPrimaryConstructor -> {
                    if (targetDeclaration is KtClass) {
                        val primaryConstructor = targetDeclaration.primaryConstructor
                        if (primaryConstructor?.matchesWithActual(declaration) == true) {
                            primaryConstructor
                        } else {
                            targetDeclaration.secondaryConstructors.find { it.matchesWithActual(declaration) }
                        }
                    } else null
                }
                is KtNamedDeclaration ->
                    when (targetDeclaration) {
                        is KtClassOrObject -> targetDeclaration.declarations.firstOrNull {
                            it is KtNamedDeclaration && it.name == declaration.name && it.matchesWithActual(declaration)
                        }
                        else -> null
                    }

                else -> null
            }?.createSmartPointer()
        }
    }
    // top level functions
    val packageFqName = declaration.containingKtFile.packageFqName
    val name = declaration.name ?: return emptySequence()
    val topLevelFqName = packageFqName.child(Name.identifier(name)).asString()
    return when (declaration) {
        is KtNamedFunction -> {
            KotlinTopLevelFunctionFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtNamedFunction::createSmartPointer)
        }

        is KtProperty -> {
            KotlinTopLevelPropertyFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtProperty::createSmartPointer)
        }

        else -> emptySequence()
    }
}

private fun KtDeclaration.matchesWithActual(actualDeclaration: KtDeclaration): Boolean {
    val declaration = this
    return declaration.hasActualModifier() && analyze(declaration) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        return symbol.getExpectsForActual().any { it.psi == actualDeclaration }
    }
}
