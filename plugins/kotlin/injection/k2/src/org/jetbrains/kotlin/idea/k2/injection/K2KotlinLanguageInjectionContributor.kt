// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
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
import org.jetbrains.kotlin.psi.KtProperty
import org.intellij.lang.annotations.Language as LanguageAnnotation

internal class K2KotlinLanguageInjectionContributor : KotlinLanguageInjectionContributorBase() {
    override val kotlinSupport: K2KotlinLanguageInjectionSupport? by lazy {
        ArrayList(InjectorUtils.getActiveInjectionSupports()).filterIsInstance(K2KotlinLanguageInjectionSupport::class.java).firstOrNull()
    }

    override fun KtCallExpression.hasCallableId(packageName: FqName, callableName: Name): Boolean = analyze(this) {
        val symbol = resolveToCall()?.singleFunctionCallOrNull()?.symbol
        symbol?.callableId == CallableId(packageName, callableName)
    }

    override fun resolveReference(reference: PsiReference): PsiElement? = reference.resolve()

    override fun getTargetProperty(ktProperty: KtProperty): KtProperty {
        // For completion property is not physical and the whole file was copied.
        // The references in that file are resolved to the original file's members
        return if (ktProperty.isPhysical) ktProperty else try { PsiTreeUtil.findSameElementInCopy(ktProperty, ktProperty.containingFile.originalFile) } catch (_: IllegalStateException) { ktProperty }
    }

    override fun injectionInfoByAnnotation(callableDeclaration: KtCallableDeclaration): InjectionInfo? =
        if (callableDeclaration.annotationEntries.isEmpty()) {
            null
        } else {
            analyze(callableDeclaration) {
                val annotation = callableDeclaration.symbol.findAnnotation<LanguageAnnotation>() ?: return null
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
            val functionSymbol = element.mainReference?.resolveToSymbol() as? KaFunctionSymbol ?: return null
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

    context(KaSession)
    private fun injectionInfoByAnnotation(injectAnnotation: KaAnnotation): InjectionInfo? {
        val languageId = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::value.name) ?: return null
        val prefix = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::prefix.name)
        val suffix = injectAnnotation.getStringValueOfArgument(LanguageAnnotation::suffix.name)
        return InjectionInfo(languageId, prefix, suffix)
    }
}

context(KaSession)
private inline fun <reified T : Annotation> KaAnnotatedSymbol.findAnnotation(): KaAnnotation? =
    annotations.find { it.classId?.asFqNameString() == T::class.java.name }

context(KaSession)
private fun KaAnnotation.getStringValueOfArgument(argumentName: String): String? {
    val argumentValueExpression =
        arguments.firstOrNull { it.name.asString() == argumentName }?.expression as? KaAnnotationValue.ConstantValue ?: return null
    return when (val argumentAsConstant = argumentValueExpression.value) {
        is KaConstantValue.StringValue -> argumentAsConstant.value
        is KaConstantValue.ErrorValue -> error("We cannot render this argument as a constant")
        else -> argumentAsConstant.render()
    }
}