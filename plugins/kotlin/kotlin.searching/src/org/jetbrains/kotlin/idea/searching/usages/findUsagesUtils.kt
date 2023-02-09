// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.usages

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyzeWithReadAction
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

internal inline fun <R> withResolvedCall(element: KtElement, crossinline block: KtAnalysisSession.(KtCall) -> R): R? {
    return analyzeWithReadAction(element) {
        element.resolveCall()?.calls?.singleOrNull()?.let { block(it) }
    }
}

fun KtFile.getDefaultImports(): List<ImportPath> {
    return platform
        .findAnalyzerServices(project)
        .getDefaultImports(project.languageVersionSettings, includeLowPriorityImports = true)
}