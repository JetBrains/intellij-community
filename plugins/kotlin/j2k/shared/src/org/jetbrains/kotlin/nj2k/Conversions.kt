// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory

@ApiStatus.Internal
abstract class Conversion(val context: NewJ2kConverterContext) {
    val symbolProvider: JKSymbolProvider
        get() = context.symbolProvider

    val typeFactory: JKTypeFactory
        get() = context.typeFactory

    context(KtAnalysisSession)
    fun runForEach(treeRoots: Sequence<JKTreeElement>, context: NewJ2kConverterContext) {
        for (root in treeRoots) {
            run(root, context)
        }
    }

    context(KtAnalysisSession)
    abstract fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext)

    protected fun <E : JKTreeElement> applyRecursive(element: E, func: (JKTreeElement) -> JKTreeElement): E =
        applyRecursiveWithData(element, data = null) { it, _ -> func(it) }

    protected fun <E : JKTreeElement, D> applyRecursiveWithData(element: E, data: D, func: (JKTreeElement, D) -> JKTreeElement): E =
        org.jetbrains.kotlin.nj2k.tree.applyRecursive(element, data, func)
}

@get:ApiStatus.Internal
val Conversion.languageVersionSettings: LanguageVersionSettings
    get() {
        val converter = context.converter
        return converter.targetModule?.languageVersionSettings ?: converter.project.languageVersionSettings
    }

@get:ApiStatus.Internal
val Conversion.moduleApiVersion: ApiVersion
    get() = languageVersionSettings.apiVersion

@ApiStatus.Internal
abstract class RecursiveConversion(context: NewJ2kConverterContext) : Conversion(context) {
    context(KtAnalysisSession)
    override fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext) {
        val root = applyToElement(treeRoot)
        assert(root === treeRoot)
    }

    context(KtAnalysisSession)
    abstract fun applyToElement(element: JKTreeElement): JKTreeElement

    context(KtAnalysisSession)
    protected fun <E : JKTreeElement> recurse(element: E): E = applyRecursive(element) { applyToElement(it) }
}

@ApiStatus.Internal
abstract class RecursiveConversionWithData<D>(context: NewJ2kConverterContext, private val initialData: D) : Conversion(context) {
    context(KtAnalysisSession)
    override fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext) {
        val root = applyToElement(treeRoot, initialData)
        assert(root === treeRoot)
    }

    abstract fun applyToElement(element: JKTreeElement, data: D): JKTreeElement

    protected fun <E : JKTreeElement> recurse(element: E, data: D): E = applyRecursiveWithData(element, data, ::applyToElement)
}