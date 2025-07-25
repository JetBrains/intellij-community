// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showYesNoCancelDialog
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.RenamerFactory
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Computes [block] and removes any possible redundant imports that would be added during this operation, not touching any existing
 * redundant imports.
 */
fun <T> modifyPsiWithOptimizedImports(file: KtFile, block: () -> T): T {
    fun unusedImports(): Set<KtImportDirective> =
        KotlinOptimizeImportsFacility.getInstance().analyzeImports(file)?.unusedImports?.toSet().orEmpty()

    val unusedImportsBefore = unusedImports()
    val result = block()
    val afterUnusedImports = unusedImports()
    val importsToRemove = afterUnusedImports - unusedImportsBefore
    importsToRemove.forEach(PsiElement::delete)
    return result
}

fun PsiElement?.canDeleteElement(): Boolean {
    if (this is KtObjectDeclaration && isObjectLiteral()) return false

    if (this is KtParameter) {
        if (parent is KtContextReceiverList) return true
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

fun checkSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>, @Nls actionString: String): List<PsiElement> {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)

    data class AnalyzedModel(
        val declaredClassRender: String,
        val overriddenDeclarationsAndRenders: Map<PsiElement, String>
    )

    val analyzeResult = analyzeInModalWindow(declaration, KotlinK2RefactoringsBundle.message("resolving.super.methods.progress.title")) {
        (declaration.symbol as? KaCallableSymbol)?.let { callableSymbol ->
            (callableSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol)?.let { containingClass ->
                val overriddenSymbols = callableSymbol.allOverriddenSymbols

                val renderToPsi = overriddenSymbols.mapNotNull {
                    it.psi?.let { psi ->
                        psi to ElementDescriptionUtil.getElementDescription(psi, RefactoringDescriptionLocation.WITH_PARENT)
                    }
                }

                val filteredDeclarations =
                  renderToPsi.filter { !ignore.contains(it.first) }

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

fun KtLambdaExpression.moveFunctionLiteralOutsideParenthesesIfPossible() {
    val valueArgument = parentOfType<KtValueArgument>()?.takeIf {
        KtPsiUtil.deparenthesize(it.getArgumentExpression()) == this
    } ?: return
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return
    val call = valueArgumentList.parent as? KtCallExpression ?: return
    val canMoveLambdaOutsideParentheses = analyze(call) { call.canMoveLambdaOutsideParentheses() }
    if (canMoveLambdaOutsideParentheses) {
        call.moveFunctionLiteralOutsideParentheses()
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
fun getThisQualifier(receiverValue: KaImplicitReceiverValue): String {
    val symbol = receiverValue.symbol
    return if ((symbol as? KaClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT) {
        //specify companion name to avoid clashes with enum entries
        (symbol.containingSymbol as KaClassifierSymbol).name!!.asString() + "." + symbol.name!!.asString()
    }
    else if ((symbol as? KaClassSymbol)?.classKind == KaClassKind.OBJECT) {
        symbol.name!!.asString()
    }
    else if (symbol is KaClassifierSymbol && symbol !is KaAnonymousObjectSymbol) {
        (symbol.psi as? PsiClass)?.name ?: ("this@" + symbol.name!!.asString())
    } else if (symbol is KaReceiverParameterSymbol && symbol.owningCallableSymbol is KaNamedSymbol) {
        // refer to this@contextReceiverType but use this@funName for everything else, because another syntax is prohibited
        (receiverValue.type.expandedSymbol?.takeIf { symbol.owningCallableSymbol.contextReceivers.isNotEmpty() }?.name ?: symbol.owningCallableSymbol.name)?.let { "this@$it" } ?: "this"
    } else {
        "this"
    }
}

/**
 * Finds a callable member of the class by its signature.
 * Only members declared in the class are checked.
 *
 * @param callableSignature The signature of the callable to be found, which includes
 * the symbol name, return type, receiver type, and value parameters.
 * @param ignoreReturnType If true, the return type is not used for comparison.
 *
 * @return The matching callable symbol if found, null otherwise.
 */
context(KaSession)
fun KaDeclarationContainerSymbol.findCallableMemberBySignature(
    callableSignature: KaCallableSignature<KaCallableSymbol>,
    ignoreReturnType: Boolean = false,
): KaCallableSymbol? = declaredMemberScope.findCallableMemberBySignature(callableSignature, ignoreReturnType)

/**
 * Finds a callable member of the class by its signature in the scope.
 *
 * @param callableSignature The signature of the callable to be found, which includes
 * the symbol name, return type, receiver type, and value parameters.
 * @param ignoreReturnType If true, the return type is not used for comparison
 *
 * @return The matching callable symbol if found, null otherwise.
 */
context(KaSession)
fun KaScope.findCallableMemberBySignature(
    callableSignature: KaCallableSignature<KaCallableSymbol>,
    ignoreReturnType: Boolean = false,
): KaCallableSymbol? {
    fun KaType?.eq(anotherType: KaType?): Boolean {
        if (this == null || anotherType == null) return this == anotherType
        return this.semanticallyEquals(anotherType)
    }

    return callables(callableSignature.symbol.name ?: return null).firstOrNull { callable ->
        fun parametersMatch(): Boolean {
            if (callableSignature is KaFunctionSignature && callable is KaFunctionSymbol) {
                if (callable.valueParameters.size != callableSignature.valueParameters.size) return false
                val allMatch = callable.valueParameters.zip(callableSignature.valueParameters)
                    .all { (it.first.returnType.eq(it.second.returnType)) }
                return allMatch
            } else {
                return callableSignature !is KaFunctionSignature && callable !is KaFunctionSymbol
            }
        }

        callable.receiverType.eq(callableSignature.receiverType) &&
                parametersMatch() &&
                (ignoreReturnType || callable.returnType.semanticallyEquals(callableSignature.returnType))
    }
}

/**
 * Returns the owner symbol of the given receiver value, or null if no owner could be found.
 */
context(KaSession)
fun KaReceiverValue.getThisReceiverOwner(): KaSymbol? {
    val symbol = when (this) {
        is KaExplicitReceiverValue -> {
            val thisRef = (KtPsiUtil.deparenthesize(expression) as? KtThisExpression)?.instanceReference ?: return null
            thisRef.resolveExpression()
        }
        is KaImplicitReceiverValue -> symbol
        is KaSmartCastedReceiverValue -> original.getThisReceiverOwner()
    }
    return symbol?.containingSymbol
}

/**
 * Rename a [KtParameter] (value or context parameter).
 * The parameter should belong to a file open in the editor.
 * A replacement via a modal dialog can happen if an attempt for inline replacement didn't succeed or is forbidden.
 * For example, if inline replacement is disabled in the editor settings.
 */
fun renameParameter(ktParameter: KtParameter, editor: Editor) {
    val pointer = ktParameter.createSmartPointer()
    PsiDocumentManager.getInstance(ktParameter.project).doPostponedOperationsAndUnblockDocument(editor.document)
    val param = pointer.element ?: return
    editor.caretModel.moveToOffset(param.startOffset)
    val dataContext = DataManager.getInstance().getDataContext(editor.component)
    RenamerFactory.EP_NAME.extensionList.flatMap { it.createRenamers(dataContext) }.firstOrNull()?.performRename()
}
