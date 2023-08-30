// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplicationWithArgumentsInfo
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.idea.base.injection.InjectionInfo
import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionContributorBase
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.intellij.lang.annotations.Language as LanguageAnnotation

internal class K2KotlinLanguageInjectionContributor : KotlinLanguageInjectionContributorBase() {
    override val kotlinSupport: K2KotlinLanguageInjectionSupport? by lazy {
        ArrayList(InjectorUtils.getActiveInjectionSupports()).filterIsInstance(K2KotlinLanguageInjectionSupport::class.java).firstOrNull()
    }

    override fun KtCallExpression.hasCallableId(packageName: FqName, callableName: Name): Boolean = analyze(this) {
        val symbol = resolveCall()?.singleFunctionCallOrNull()?.symbol
        symbol?.callableIdIfNonLocal == CallableId(packageName, callableName)
    }

    override fun resolveReference(reference: PsiReference): PsiElement? = reference.resolve()

    override fun injectionInfoByAnnotation(callableDeclaration: KtCallableDeclaration): InjectionInfo? =
        if (callableDeclaration.annotationEntries.isEmpty()) {
            null
        } else {
            analyze(callableDeclaration) {
                val annotation = callableDeclaration.getSymbol().findAnnotation<LanguageAnnotation>() ?: return null
                injectionInfoByAnnotation(annotation)
            }
        }

    override fun injectionInfoByParameterAnnotation(
        functionReference: KtReference,
        argumentName: Name?,
        argumentIndex: Int
    ): InjectionInfo? {
        val element = functionReference.element as? KtElement ?: return null
        return analyze(element) {
            val functionSymbol = element.mainReference?.resolveToSymbol() as? KtFunctionLikeSymbol ?: return null
            val parameterSymbol = if (argumentName != null) {
                functionSymbol.valueParameters.firstOrNull { it.name == argumentName }
            } else {
                functionSymbol.valueParameters.getOrNull(argumentIndex)
            } ?: return null

            // For a parameter of a primary constructor, there are multiple possible locations for the annotation in the generated Java
            // bytecode e.g., getter. Thus, we cannot get annotations of the parameter symbol itself. We have to check its use-site targets.
            // We first check its generated property here.
            val annotationForParameter = parameterSymbol.generatedPrimaryConstructorProperty?.findAnnotation<LanguageAnnotation>()
                ?: parameterSymbol.findAnnotation<LanguageAnnotation>() ?: return null
            injectionInfoByAnnotation(annotationForParameter)
        }
    }

    context(KtAnalysisSession)
    private fun injectionInfoByAnnotation(injectAnnotation: KtAnnotationApplicationWithArgumentsInfo): InjectionInfo? {
        val languageId = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::value.name) ?: return null
        val prefix = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::prefix.name)
        val suffix = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::suffix.name)
        return InjectionInfo(languageId, prefix, suffix)
    }
}

context(KtAnalysisSession)
private inline fun <reified T : Annotation> KtAnnotatedSymbol.findAnnotation(): KtAnnotationApplicationWithArgumentsInfo? =
    annotations.find { it.classId?.asFqNameString() == T::class.java.name }

context(KtAnalysisSession)
private fun KtAnnotationApplicationWithArgumentsInfo.getStringValueOfArgument(argumentName: String): String? {
    val argumentValueExpression =
        arguments.firstOrNull { it.name.asString() == argumentName }?.expression as? KtConstantAnnotationValue ?: return null
    return when (val argumentAsConstant = argumentValueExpression.constantValue) {
        is KtConstantValue.KtStringConstantValue -> argumentAsConstant.value
        is KtConstantValue.KtErrorConstantValue -> error("We cannot render this argument as a constant")
        else -> argumentAsConstant.renderAsKotlinConstant()
    }
}