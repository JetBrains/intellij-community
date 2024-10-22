// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.injection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.jetbrains.kotlin.base.fe10.analysis.findAnnotation
import org.jetbrains.kotlin.base.fe10.analysis.getStringValue
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.idea.base.injection.InjectionInfo
import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionContributorBase
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.intellij.lang.annotations.Language as LanguageAnnotation

internal class KotlinLanguageInjectionContributor : KotlinLanguageInjectionContributorBase() {
    override val kotlinSupport: KotlinLanguageInjectionSupport? by lazy {
        InjectorUtils.getActiveInjectionSupports().filterIsInstance<KotlinLanguageInjectionSupport>().firstOrNull()
    }

    override fun KtCallExpression.hasCallableId(packageName: FqName, callableName: Name): Boolean {
        val fqName = resolveToCall(BodyResolveMode.PARTIAL)?.candidateDescriptor?.fqNameOrNull() ?: return false
        return fqName.parent() == packageName && fqName.shortName() == callableName
    }

    override fun resolveReference(reference: PsiReference): PsiElement? = allowResolveInDispatchThread { reference.resolve() }

    override fun injectionInfoByAnnotation(callableDeclaration: KtCallableDeclaration): InjectionInfo? {
        // no injections in kotlin stdlib
        val packageName = callableDeclaration.containingKtFile.packageFqName.asString()
        val kotlinPackage = StandardNames.BUILT_INS_PACKAGE_NAME.asString()
        if (packageName == kotlinPackage || packageName.startsWith("${kotlinPackage}.")) return null

        val descriptor = allowResolveInDispatchThread { callableDeclaration.descriptor } ?: return null
        return injectionInfoByAnnotation(descriptor)
    }

    override fun injectionInfoByParameterAnnotation(
        functionReference: KtReference,
        argumentName: Name?,
        argumentIndex: Int
    ): InjectionInfo? {
        val functionDescriptor = allowResolveInDispatchThread {
            val bindingContext = functionReference.element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
            functionReference.resolveToDescriptors(bindingContext).singleOrNull() as? FunctionDescriptor
        } ?: return null

        val parameterDescriptor = if (argumentName != null) {
            functionDescriptor.valueParameters.firstOrNull { it.name == argumentName }
        } else {
            functionDescriptor.valueParameters.getOrNull(argumentIndex)
        } ?: return null
        return injectionInfoByAnnotation(parameterDescriptor)
    }

    private fun injectionInfoByAnnotation(annotated: Annotated): InjectionInfo? {
        val injectAnnotation = annotated.findAnnotation<LanguageAnnotation>() ?: return null
        val languageId = injectAnnotation.getStringValue(LanguageAnnotation::value) ?: return null
        val prefix = injectAnnotation.getStringValue(LanguageAnnotation::prefix)
        val suffix = injectAnnotation.getStringValue(LanguageAnnotation::suffix)
        return InjectionInfo(languageId, prefix, suffix)
    }
}