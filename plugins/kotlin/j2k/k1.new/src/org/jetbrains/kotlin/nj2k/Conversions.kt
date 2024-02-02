// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory

internal abstract class Conversion(val context: NewJ2kConverterContext) {
    val symbolProvider: JKSymbolProvider
        get() = context.symbolProvider

    val typeFactory: JKTypeFactory
        get() = context.typeFactory

    fun runForEach(treeRoots: Sequence<JKTreeElement>, context: NewJ2kConverterContext) {
        for (root in treeRoots) {
            run(root, context)
        }
    }

    abstract fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext)

    protected fun <E : JKTreeElement> applyRecursive(element: E, func: (JKTreeElement) -> JKTreeElement): E =
        applyRecursiveWithData(element, data = null) { it, _ -> func(it) }

    protected fun <E : JKTreeElement, D> applyRecursiveWithData(element: E, data: D, func: (JKTreeElement, D) -> JKTreeElement): E =
        org.jetbrains.kotlin.nj2k.tree.applyRecursive(element, data, func)
}

internal val Conversion.languageVersionSettings: LanguageVersionSettings
    get() {
        val converter = context.converter
        return converter.targetModule?.languageVersionSettings ?: converter.project.languageVersionSettings
    }

internal val Conversion.moduleApiVersion: ApiVersion
    get() = languageVersionSettings.apiVersion

internal abstract class RecursiveConversion(context: NewJ2kConverterContext) : Conversion(context) {
    override fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext) {
        val root = applyToElement(treeRoot)
        assert(root === treeRoot)
    }

    abstract fun applyToElement(element: JKTreeElement): JKTreeElement

    protected fun <E : JKTreeElement> recurse(element: E): E = applyRecursive(element, ::applyToElement)
}

internal abstract class RecursiveConversionWithData<D>(context: NewJ2kConverterContext, private val initialData: D) : Conversion(context) {
    override fun run(treeRoot: JKTreeElement, context: NewJ2kConverterContext) {
        val root = applyToElement(treeRoot, initialData)
        assert(root === treeRoot)
    }

    abstract fun applyToElement(element: JKTreeElement, data: D): JKTreeElement

    protected fun <E : JKTreeElement> recurse(element: E, data: D): E = applyRecursiveWithData(element, data, ::applyToElement)
}