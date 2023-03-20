// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.fe10.binding

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtSymbolBasedReference
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.fir.fe10.*
import org.jetbrains.kotlin.idea.references.FE10_BINDING_RESOLVE_TO_DESCRIPTORS
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ToDescriptorBindingContextValueProviders(bindingContext: KtSymbolBasedBindingContext) {
    private val context = bindingContext.context
    private val declarationToDescriptorGetters = mutableListOf<(PsiElement) -> DeclarationDescriptor?>()

    private inline fun <reified K : PsiElement, V : DeclarationDescriptor> KtSymbolBasedBindingContext.registerDeclarationToDescriptorByKey(
        slice: ReadOnlySlice<K, V>,
        crossinline getter: (K) -> V?
    ) {
        declarationToDescriptorGetters.add {
            if (it is K) getter(it) else null
        }
        registerGetterByKey(slice, { getter(it) })
    }

    init {
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.CLASS, this::getClass)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.TYPE_PARAMETER, this::getTypeParameter)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.FUNCTION, this::getFunction)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.CONSTRUCTOR, this::getConstructor)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.VARIABLE, this::getVariable)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.VALUE_PARAMETER, this::getValueParameter)
        bindingContext.registerDeclarationToDescriptorByKey(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, this::getPrimaryConstructorParameter)

        bindingContext.registerGetterByKey(FE10_BINDING_RESOLVE_TO_DESCRIPTORS, this::resolveToDescriptors)
        bindingContext.registerGetterByKey(BindingContext.DECLARATION_TO_DESCRIPTOR, this::getDeclarationToDescriptor)
    }

    private inline fun <reified T : Any> PsiElement.getKtSymbolOfTypeOrNull(): T? =
        this@ToDescriptorBindingContextValueProviders.context.withAnalysisSession {
            this@getKtSymbolOfTypeOrNull.safeAs<KtDeclaration>()?.getSymbol().safeAs<T>()
        }

    private fun getClass(key: PsiElement): ClassDescriptor? {
        val ktClassSymbol = key.getKtSymbolOfTypeOrNull<KtNamedClassOrObjectSymbol>() ?: return null

        return KtSymbolBasedClassDescriptor(ktClassSymbol, context)
    }

    private fun getTypeParameter(key: KtTypeParameter): TypeParameterDescriptor {
        val ktTypeParameterSymbol = context.withAnalysisSession { key.getTypeParameterSymbol() }
        return KtSymbolBasedTypeParameterDescriptor(ktTypeParameterSymbol, context)
    }

    private fun getFunction(key: PsiElement): SimpleFunctionDescriptor? {
        val ktFunctionLikeSymbol = key.getKtSymbolOfTypeOrNull<KtFunctionLikeSymbol>() ?: return null
        return ktFunctionLikeSymbol.toDeclarationDescriptor(context) as? SimpleFunctionDescriptor
    }

    private fun getConstructor(key: PsiElement): ConstructorDescriptor? {
        val ktConstructorSymbol = key.getKtSymbolOfTypeOrNull<KtConstructorSymbol>() ?: return null
        val containerClass = context.withAnalysisSession { ktConstructorSymbol.getContainingSymbol() }
        check(containerClass is KtNamedClassOrObjectSymbol) {
            "Unexpected contained for Constructor symbol: $containerClass, ktConstructorSymbol = $ktConstructorSymbol"
        }

        return KtSymbolBasedConstructorDescriptor(ktConstructorSymbol, KtSymbolBasedClassDescriptor(containerClass, context))
    }

    private fun getVariable(key: PsiElement): VariableDescriptor? {
        if (key !is KtVariableDeclaration) return null

        if (key is KtProperty) {
            val symbol = context.withAnalysisSession { key.getVariableSymbol() }
            return symbol.toDeclarationDescriptor(context)
        } else {
            context.implementationPostponed("Destruction declaration is not supported yet: $key")
        }
    }

    private fun getValueParameter(key: KtParameter): VariableDescriptor? {
        val symbol = context.withAnalysisSession { key.getParameterSymbol() }.safeAs<KtValueParameterSymbol>() ?: return null
        return symbol.toDeclarationDescriptor(context)
    }

    private fun getPrimaryConstructorParameter(key: PsiElement): PropertyDescriptor? {
        val parameter = key.safeAs<KtParameter>() ?: return null
        val parameterSymbol = context.withAnalysisSession { parameter.getParameterSymbol() }
        val propertySymbol = parameterSymbol.safeAs<KtValueParameterSymbol>()?.generatedPrimaryConstructorProperty ?: return null
        return KtSymbolBasedPropertyDescriptor(propertySymbol, context)
    }

    private fun getDeclarationToDescriptor(key: PsiElement): DeclarationDescriptor? {
        for (getter in declarationToDescriptorGetters) {
            getter(key)?.let { return it }
        }
        return null
    }

    private fun resolveToDescriptors(ktReference: KtReference): Collection<DeclarationDescriptor>? {
        if (ktReference !is KtSymbolBasedReference) return null

        val symbols = context.withAnalysisSession { ktReference.resolveToSymbols() }
        return symbols.map { it.toDeclarationDescriptor(context) }
    }
}
