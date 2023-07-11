// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtSymbolBasedReference
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.intentions.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.load.java.propertyNameBySetMethodName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name.identifier
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * At the moment, this implementation of [KtReferenceMutateService] is not able to do any of required operations. It is OK and
 * on purpose - this functionality will be added later.
 */
internal class K2ReferenceMutateService : KtReferenceMutateServiceBase() {
    override fun bindToFqName(simpleNameReference: KtSimpleNameReference,
                              fqName: FqName,
                              shorteningMode: KtSimpleNameReference.ShorteningMode,
                              targetElement: PsiElement?): PsiElement {
        operationNotSupportedInK2Error()
    }

    override fun KtSimpleReference<KtNameReferenceExpression>.suggestVariableName(
        expr: KtExpression,
        context: PsiElement): String {
        @OptIn(KtAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(expr) {
                with(KotlinNameSuggester(KotlinNameSuggester.Case.CAMEL)) {
                    return suggestExpressionNames(expr).first()
                }
            }
        }
    }

    override fun handleElementRename(ktReference: KtReference, newElementName: String): PsiElement? {
        val newNameAsName = identifier(newElementName)
        val newName = if (ktReference is KtSimpleNameReference && JvmAbi.isSetterName(newElementName)) {
            propertyNameBySetMethodName(newNameAsName,
                                        withIsPrefix = ktReference.expression.getReferencedNameAsName().asString().startsWith("is"))
        }
        else if (ktReference is KtSimpleNameReference && JvmAbi.isGetterName(newElementName)) {
            propertyNameByGetMethodName(newNameAsName)
        }
        else null

        if (newName == null && ktReference is KtSymbolBasedReference) {
            @OptIn(KtAllowAnalysisOnEdt::class)
            allowAnalysisOnEdt {
                analyze(ktReference.element) {
                    val symbol = ktReference.resolveToSymbol() as? KtSyntheticJavaPropertySymbol
                    if (symbol != null) {
                        val isGetter = (ktReference.resolve() as? PsiMethod)?.let { JvmAbi.isGetterName(it.name) }?: false
                        @Suppress("UNCHECKED_CAST")
                        return (ktReference as KtSimpleReference<KtNameReferenceExpression>).renameToOrdinaryMethod(newElementName, isGetter)
                    }
                }
            }
        }

        return super.handleElementRename(ktReference, newName?.asString() ?: newElementName)
    }

    override fun replaceWithImplicitInvokeInvocation(newExpression: KtDotQualifiedExpression): KtExpression? =
        OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(newExpression)

    private fun operationNotSupportedInK2Error(): Nothing {
        throw IncorrectOperationException("K2 plugin does not yet support this operation")
    }
}