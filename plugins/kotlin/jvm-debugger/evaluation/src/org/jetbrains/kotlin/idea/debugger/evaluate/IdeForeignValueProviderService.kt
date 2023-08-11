// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.sun.jdi.Value
import org.jetbrains.kotlin.analysis.providers.ForeignValueProviderService
import org.jetbrains.kotlin.psi.KtCodeFragment

internal class IdeForeignValueProviderService : ForeignValueProviderService {
    override fun getForeignValues(codeFragment: KtCodeFragment): Map<String, String> {
        val project = codeFragment.project
        val context = codeFragment.context
        val debugProcess = DebugContextProvider.getDebuggerContext(project, context)?.debugProcess ?: return emptyMap()
        val valueMarkers = DebuggerUtilsImpl.getValueMarkers(debugProcess)?.allMarkers ?: return emptyMap()

        return buildMap {
            for ((value, markup) in valueMarkers) {
                if (value is Value) {
                    val valueName = markup.text + CodeFragmentFactoryContextWrapper.DEBUG_LABEL_SUFFIX
                    val typeDescriptor = value.type().signature()
                    put(valueName, typeDescriptor)
                }
            }
        }
    }
}