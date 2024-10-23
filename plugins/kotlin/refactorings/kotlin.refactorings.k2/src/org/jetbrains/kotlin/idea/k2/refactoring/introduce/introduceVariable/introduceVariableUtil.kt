// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.extractDataClassParameterNames
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun chooseDestructuringNames(
    editor: Editor?,
    expression: KtExpression,
    nameValidator: KotlinDeclarationNameValidator,
    callback: (SuggestedNames) -> Unit
) {
    val destructuringNames = suggestDestructuringNames(expression, nameValidator) ?: return callback(emptyList())

    if (destructuringNames.size <= 1) return callback(emptyList())

    if (isUnitTestMode()) return callback(destructuringNames)

    if (editor == null) return callback(emptyList())

    val singleVariable = KotlinBundle.message("text.create.single.variable")
    val listOfVariants = listOf(
        singleVariable,
        KotlinBundle.message("text.create.destructuring.declaration"),
    )

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(listOfVariants)
        .setMovable(true)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback { callback(if (it == singleVariable) emptyList() else destructuringNames) }
        .createPopup()
        .showInBestPositionFor(editor)
}

// a copy-paste of `org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateFragment` from `idea.kotlin` module
internal fun findStringTemplateFragment(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry.parent !is KtStringTemplateExpression || startEntry.parent != endEntry.parent) return null

    val prefixOffset = startOffset - startEntry.startOffset
    if (startEntry !is KtLiteralStringTemplateEntry && prefixOffset > 0) return null

    val suffixOffset = endOffset - endEntry.startOffset
    if (endEntry !is KtLiteralStringTemplateEntry && suffixOffset < endEntry.textLength) return null

    val prefix = startEntry.text.substring(0, prefixOffset)
    val suffix = endEntry.text.substring(suffixOffset)

    return K2ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix).createExpression()
}

fun suggestSingleVariableNames(
    expression: KtExpression,
    nameValidator: KotlinDeclarationNameValidator,
): SuggestedNames {
    return with(KotlinNameSuggester()) {
        analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            listOf(suggestExpressionNames(expression) { nameValidator.validate(it) }.toList())
        }
    }
}

/**
 * Suggests names for a destructuring declaration if it is possible.
 * Considers the maximum possible number of variables in the destructuring declaration, taking into
 * account all properties declared in the primary constructor in the case of a data class, as well as
 * `componentN()` functions.
 *
 * @param expression The expression for which destructuring names are to be suggested.
 * @param validator A validator that adjusts suggested names by adding a numeric suffix in case of conflicts.
 * @return A list of suggested names for the destructuring declaration if it is possible, or null if not.
*/
private fun suggestDestructuringNames(
    expression: KtExpression,
    validator: KotlinDeclarationNameValidator,
): SuggestedNames? {
    return analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
        val expressionType = expression.expressionType?.lowerBoundIfFlexible() as? KaClassType ?: return@analyzeInModalWindow null
        val dataClassParameterNames = extractDataClassParameterNames(expressionType) ?: emptyList()
        val applicableComponents = extractApplicableComponents(expression, expressionType, dataClassParameterNames)
        val usedNames = mutableSetOf<String>()
        val combinedValidator: (String) -> Boolean = { validator.validate(it) && !usedNames.contains(it) }
        val namesForDataClassParameters = if (dataClassParameterNames.isNotEmpty())
            suggestNamesForDataClassParameters(
                dataClassParameterNames,
                usedNames,
                combinedValidator,
            )
        else emptyList()

        val namesForComponentOperators = if (applicableComponents.isNotEmpty()) {
            suggestNamesForComponentOperators(
                applicableComponents,
                usedNames,
                combinedValidator,
            )
        } else emptyList()

        namesForDataClassParameters + namesForComponentOperators
    }
}

private fun KaSession.suggestNamesForDataClassParameters(
    parameterNames: List<String>,
    usedNames: MutableSet<String>,
    validator: (String) -> Boolean,
): SuggestedNames {
    return parameterNames.map { name ->
        listOf(suggestNameByName(name) { validator(it) }.also { usedNames += it })
    }
}

private fun KaSession.suggestNamesForComponentOperators(
    components: List<KaNamedFunctionSymbol>,
    usedNames: MutableSet<String>,
    validator: (String) -> Boolean,
): SuggestedNames {
    return with(KotlinNameSuggester()) {
        components.map { component ->
            val typeNames = suggestTypeNames(component.returnType)
                .map { name -> suggestNameByName(name, validator) }
                .toList()
                .also { names -> usedNames += names }

            val componentName = suggestNameByName(component.name.asString()) { name -> validator(name) }
                .also { usedNames += it }

            buildList {
                addAll(typeNames)
                add(componentName)
            }
        }
    }
}

private fun KaSession.extractApplicableComponents(
    expression: KtExpression,
    expressionType: KaType,
    parameterNames: List<String>,
): List<KaNamedFunctionSymbol> {
    val result = mutableListOf<KaNamedFunctionSymbol>()
    val components = extractComponents(expression, expressionType)
    for (i in generateSequence(parameterNames.size + 1) { it + 1 }) {
        val componentName = "component$i"
        val foundComponent = components.find { it.name.asString() == componentName }
        if (foundComponent != null) {
            result.add(foundComponent)
        } else break
    }
    return result
}

private fun KaSession.extractComponents(
    expression: KtExpression,
    type: KaType,
): Set<KaNamedFunctionSymbol> {
    if (type.isArrayOrPrimitiveArray) return emptySet()
    val scope = expression.containingKtFile.scopeContext(expression).compositeScope()
    return scope.callables { it.asString().startsWith("component") }
        .filterIsInstance<KaNamedFunctionSymbol>()
        .filter {
            it.isOperator &&
                    it.valueParameters.isEmpty() &&
                    it.receiverType?.semanticallyEquals(type) == true
        }.toSet()
}
