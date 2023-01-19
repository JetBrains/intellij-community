// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showYesNoCancelDialog
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyzeInModalWindow
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*

fun PsiElement?.canDeleteElement(): Boolean {
    if (this is KtObjectDeclaration && isObjectLiteral()) return false

    if (this is KtParameter) {
        val parameterList = parent as? KtParameterList ?: return false
        val declaration = parameterList.parent as? KtDeclaration ?: return false
        return declaration !is KtPropertyAccessor
    }

    return this is KtClassOrObject
            || this is KtSecondaryConstructor
            || this is KtNamedFunction
            || this is KtProperty
            || this is KtTypeParameter
            || this is KtTypeAlias
}

fun checkSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?, @Nls actionString: String): List<PsiElement> {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)

    data class AnalyzedModel(
        val declaredClassRender: String,
        val overriddenDeclarationsAndRenders: Map<PsiElement, String>
    )

    val analyzeResult = analyzeInModalWindow(declaration, KotlinK2RefactoringsBundle.message("resolving.super.methods.progress.title")) {
        (declaration.getSymbol() as? KtCallableSymbol)?.let { callableSymbol ->
            callableSymbol.originalContainingClassForOverride?.let { containingClass ->
                val overriddenSymbols = callableSymbol.getAllOverriddenSymbols()

                val renderToPsi = overriddenSymbols.mapNotNull {
                    it.psi?.let { psi ->
                        psi to ElementDescriptionUtil.getElementDescription(psi, RefactoringDescriptionLocation.WITH_PARENT)
                    }
                }

                val filteredDeclarations =
                    if (ignore != null) renderToPsi.filter { !ignore.contains(it.first) } else renderToPsi

                val renderedClass = containingClass.name?.asString() ?: SpecialNames.ANONYMOUS_STRING //TODO render class

                AnalyzedModel(renderedClass, filteredDeclarations.toMap())
            }
        }
    } ?: return listOf(declaration)

    if (analyzeResult.overriddenDeclarationsAndRenders.isEmpty()) return listOf(declaration)

    val message = KotlinK2RefactoringsBundle.message(
        "override.declaration.x.overrides.y.in.class.list",
        analyzeResult.declaredClassRender,
        "\n${analyzeResult.overriddenDeclarationsAndRenders.values.joinToString(separator = "")}",
        actionString
    )

    val exitCode = if (isUnitTestMode()) Messages.YES else showYesNoCancelDialog(
        declaration.project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon()
    )

    return when (exitCode) {
        Messages.YES -> analyzeResult.overriddenDeclarationsAndRenders.keys.toList()
        Messages.NO -> listOf(declaration)
        else -> emptyList()
    }
}
