// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.AdditionalContextProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinForeignValueProviderService
import org.jetbrains.kotlin.psi.KtCodeFragment

internal class IdeForeignValueProviderService : KotlinForeignValueProviderService {
    override fun getForeignValues(codeFragment: KtCodeFragment): Map<String, String> {
        val project = codeFragment.project
        val context = codeFragment.context
        val additionalContextElements = AdditionalContextProvider.getAllAdditionalContextElements(project, context)
        return additionalContextElements.associate { it.name to it.jvmSignature }
    }
}