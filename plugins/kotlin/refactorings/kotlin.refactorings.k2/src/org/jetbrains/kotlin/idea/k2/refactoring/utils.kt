// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showYesNoCancelDialog
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.refactoring.isComplexCallWithLambdaArgument
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Computes [block] and removes any possible redundant imports that would be added during this operation, not touching any existing
 * redundant imports.
 */
fun <T> computeWithoutAddingRedundantImports(file: KtFile, block: () -> T): T {
    fun unusedImports() = KotlinOptimizeImportsFacility.getInstance().analyzeImports(file)?.unusedImports?.toSet().orEmpty()
    val unusedImportsBefore = unusedImports()
    val result = block()
    val afterUnusedImports = unusedImports()
    val importsToRemove =  afterUnusedImports - unusedImportsBefore
    importsToRemove.forEach(PsiElement::delete)
    return result
}

@JvmOverloads
fun getOrCreateKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile =
    (targetDir.findFile(fileName) ?: createKotlinFile(fileName, targetDir, packageName)) as KtFile

fun createKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile {
    targetDir.checkCreateFile(fileName)
    val packageFqName = packageName?.let(::FqName) ?: FqName.ROOT
    val file = PsiFileFactory.getInstance(targetDir.project).createFileFromText(
        fileName, KotlinFileType.INSTANCE, if (!packageFqName.isRoot) "package ${packageFqName.quoteIfNeeded()} \n\n" else ""
    )
    return targetDir.add(file) as KtFile
}

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

fun checkSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>, @Nls actionString: String): List<PsiElement> {
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

fun KtCallExpression.canMoveLambdaOutsideParentheses(skipComplexCalls: Boolean = true): Boolean {
    if (skipComplexCalls && isComplexCallWithLambdaArgument()) {
        return false
    }

    if (getStrictParentOfType<KtDelegatedSuperTypeEntry>() != null) {
        return false
    }
    val lastLambdaExpression = getLastLambdaExpression() ?: return false

    fun KtExpression.parentLabeledExpression(): KtLabeledExpression? {
        return getStrictParentOfType<KtLabeledExpression>()?.takeIf { it.baseExpression == this }
    }

    if (lastLambdaExpression.parentLabeledExpression()?.parentLabeledExpression() != null) {
        return false
    }

    val callee = calleeExpression
    if (callee !is KtNameReferenceExpression) return true

    analyze(callee) {
        val resolveCall = callee.resolveCall() ?: return false
        val call = resolveCall.successfulFunctionCallOrNull()

        fun KtType.isFunctionalType(): Boolean = this is KtTypeParameterType || isSuspendFunctionType || isFunctionType ||  isFunctionalInterfaceType

        if (call == null) {
            val paramType = resolveCall.successfulVariableAccessCall()?.partiallyAppliedSymbol?.symbol?.returnType
            if (paramType != null && paramType.isFunctionalType()) {
                return true
            }
            val calls = (resolveCall as KtErrorCallInfo).candidateCalls.filterIsInstance<KtSimpleFunctionCall>()

            return calls.isEmpty() || calls.all { functionalCall ->
                val lastParameter = functionalCall.partiallyAppliedSymbol.signature.valueParameters.lastOrNull()
                val lastParameterType = lastParameter?.returnType
                lastParameterType != null && lastParameterType.isFunctionalType()
            }
        }

        val lastParameter = call.argumentMapping[lastLambdaExpression] ?: return false
        if (lastParameter.symbol != call.partiallyAppliedSymbol.signature.valueParameters.lastOrNull()?.symbol) {
            return false
        }

        return lastParameter.returnType.isFunctionalType()
    }
    return false
}