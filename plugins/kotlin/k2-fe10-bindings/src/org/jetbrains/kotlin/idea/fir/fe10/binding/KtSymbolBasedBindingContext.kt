// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.fe10.binding

import com.google.common.collect.ImmutableMap
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.fir.fe10.Fe10WrapperContext
import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KtSymbolBasedBindingContext(val context: Fe10WrapperContext) : BindingContext {
    private val LOG = Logger.getInstance(KtSymbolBasedBindingContext::class.java)

    private val getterBySlice: MutableMap<ReadOnlySlice<*, *>, (Nothing) -> Any?> = hashMapOf()

    init {
        CallAndResolverCallWrappers(this)
        ToDescriptorBindingContextValueProviders(this)
        MiscBindingContextValueProvider(this)
        Fe10BindingScopeProvider(this)
    }

    fun <K, V> registerGetterByKey(slice: ReadOnlySlice<K, V>, getter: (K) -> V?) {
        check(!getterBySlice.containsKey(slice)) {
            "Key $slice already registered: ${getterBySlice[slice]}"
        }
        getterBySlice[slice] = getter
    }

    override fun getDiagnostics(): Diagnostics = context.incorrectImplementation { Diagnostics.EMPTY }

    override fun <K : Any?, V : Any?> get(slice: ReadOnlySlice<K, V>, key: K): V? {
        val getter = getterBySlice[slice]
        if (getter == null) {
            if (context.enableLogging) LOG.warn("Key not registered: $slice")
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val v = if (key == null) null else (getter as (K) -> V?)(key)

        if (context.enableLogging) {
            val psiText = key.safeAs<PsiElement>()?.text ?: key
            println("$slice: $psiText -> $v")
        }

        return v
    }

    override fun getType(expression: KtExpression): KotlinType? =
        context.withAnalysisSession {
            expression.getKtType()?.toKotlinType(context)
        }

    override fun <K : Any?, V : Any?> getKeys(slice: WritableSlice<K, V>?): Collection<K> =
        context.noImplementation()

    override fun <K : Any?, V : Any?> getSliceContents(slice: ReadOnlySlice<K, V>): ImmutableMap<K, V> =
        context.noImplementation()

    override fun addOwnDataTo(trace: BindingTrace, commitDiagnostics: Boolean) =
        context.noImplementation()
}