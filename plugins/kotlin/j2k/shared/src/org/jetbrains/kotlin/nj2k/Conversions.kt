// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory

abstract class Conversion(val context: ConverterContext) {
    val symbolProvider: JKSymbolProvider
        get() = context.symbolProvider

    val typeFactory: JKTypeFactory
        get() = context.typeFactory

    context(_: KaSession)
    fun runForEach(treeRoots: Sequence<JKTreeElement>, context: ConverterContext) {
        for (root in treeRoots) {
            run(root, context)
        }
    }

    context(_: KaSession)
    abstract fun run(treeRoot: JKTreeElement, context: ConverterContext)

    open fun isEnabledInBasicMode(): Boolean = true

    protected fun <E : JKTreeElement> applyRecursive(element: E, func: (JKTreeElement) -> JKTreeElement): E =
        applyRecursiveWithData(element, data = null) { it, _ -> func(it) }

    protected fun <E : JKTreeElement, D> applyRecursiveWithData(element: E, data: D, func: (JKTreeElement, D) -> JKTreeElement): E =
        org.jetbrains.kotlin.nj2k.tree.applyRecursive(element, data, func)
}

val Conversion.languageVersionSettings: LanguageVersionSettings
    get() {
        val converter = context.converter
        return converter.targetModule?.languageVersionSettings ?: converter.project.languageVersionSettings
    }

val Conversion.moduleApiVersion: ApiVersion
    get() = languageVersionSettings.apiVersion

abstract class RecursiveConversion(context: ConverterContext) : Conversion(context) {
    context(_: KaSession)
    override fun run(treeRoot: JKTreeElement, context: ConverterContext) {
        val root = applyToElement(treeRoot)
        assert(root === treeRoot)
    }

    context(_: KaSession)
    abstract fun applyToElement(element: JKTreeElement): JKTreeElement

    context(_: KaSession)
    protected fun <E : JKTreeElement> recurse(element: E): E = applyRecursive(element) { applyToElement(it) }
}

abstract class RecursiveConversionWithData<D>(context: ConverterContext, private val initialData: D) : Conversion(context) {
    context(_: KaSession)
    override fun run(treeRoot: JKTreeElement, context: ConverterContext) {
        val root = applyToElement(treeRoot, initialData)
        assert(root === treeRoot)
    }

    abstract fun applyToElement(element: JKTreeElement, data: D): JKTreeElement

    protected fun <E : JKTreeElement> recurse(element: E, data: D): E = applyRecursiveWithData(element, data, ::applyToElement)
}