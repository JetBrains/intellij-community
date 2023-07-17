// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.fe10bindings.inspections

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeTokenProvider
import org.jetbrains.kotlin.idea.fir.fe10.KtLifetimeTokenForKtSymbolBasedWrappers
import org.jetbrains.kotlin.idea.fir.fe10.KtLifetimeTokenForKtSymbolBasedWrappersFactory

@OptIn(KtAnalysisApiInternals::class)
internal fun Project.registerLifetimeTokenFactoryForFe10Binding(disposable: Disposable) {
    val token = KtLifetimeTokenForKtSymbolBasedWrappers(this)
    val factory = KtLifetimeTokenForKtSymbolBasedWrappersFactory(token)
    registerOrReplaceServiceInstance(
        KtLifetimeTokenProvider::class.java,
        object : KtLifetimeTokenProvider() {
            override fun getLifetimeTokenFactory() = factory
        },
        parentDisposable = disposable
    )
}