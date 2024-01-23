// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory

internal interface Conversion {
    val context: NewJ2kConverterContext

    val symbolProvider: JKSymbolProvider
        get() = context.symbolProvider

    val typeFactory: JKTypeFactory
        get() = context.typeFactory

    fun runConversion(treeRoots: Sequence<JKTreeElement>, context: NewJ2kConverterContext): Boolean
}

internal interface SequentialBaseConversion : Conversion {
    fun runConversion(treeRoot: JKTreeElement, context: NewJ2kConverterContext): Boolean

    override fun runConversion(treeRoots: Sequence<JKTreeElement>, context: NewJ2kConverterContext): Boolean {
        return treeRoots.maxOfOrNull { runConversion(it, context) } ?: false
    }
}

internal abstract class MatchBasedConversion(override val context: NewJ2kConverterContext) : SequentialBaseConversion {
    fun <R : JKTreeElement, T> applyRecursive(element: R, data: T, func: (JKTreeElement, T) -> JKTreeElement): R =
        org.jetbrains.kotlin.nj2k.tree.applyRecursive(element, data, ::onElementChanged, func)

    fun <R : JKTreeElement> applyRecursive(element: R, func: (JKTreeElement) -> JKTreeElement): R {
        return applyRecursive(element, null) { it, _ -> func(it) }
    }

    private inline fun <T> applyRecursiveToList(
        element: JKTreeElement,
        child: List<JKTreeElement>,
        iter: MutableListIterator<Any>,
        data: T,
        func: (JKTreeElement, T) -> JKTreeElement
    ): List<JKTreeElement> {

        val newChild = child.map {
            func(it, data)
        }

        child.forEach { it.detach(element) }
        iter.set(child)
        newChild.forEach { it.attach(element) }
        newChild.zip(child).forEach { (old, new) ->
            if (old !== new) {
                onElementChanged(new, old)
            }
        }
        return newChild
    }


    abstract fun onElementChanged(new: JKTreeElement, old: JKTreeElement)
}

internal abstract class RecursiveApplicableConversionBase(context: NewJ2kConverterContext) : MatchBasedConversion(context) {
    override fun onElementChanged(new: JKTreeElement, old: JKTreeElement) {
        somethingChanged = true
    }

    override fun runConversion(treeRoot: JKTreeElement, context: NewJ2kConverterContext): Boolean {
        val root = applyToElement(treeRoot)
        assert(root === treeRoot)
        return somethingChanged
    }

    private var somethingChanged = false

    abstract fun applyToElement(element: JKTreeElement): JKTreeElement

    fun <T : JKTreeElement> recurse(element: T): T = applyRecursive(element, ::applyToElement)
}

internal val RecursiveApplicableConversionBase.languageVersionSettings: LanguageVersionSettings
    get() {
        val converter = context.converter
        return converter.targetModule?.languageVersionSettings ?: converter.project.languageVersionSettings
    }

internal val RecursiveApplicableConversionBase.moduleApiVersion: ApiVersion
    get() = languageVersionSettings.apiVersion

internal abstract class RecursiveApplicableConversionWithState<S>(
    context: NewJ2kConverterContext,
    private val initialState: S
) : MatchBasedConversion(context) {
    override fun onElementChanged(new: JKTreeElement, old: JKTreeElement) {
        somethingChanged = true
    }

    private var somethingChanged = false

    override fun runConversion(treeRoot: JKTreeElement, context: NewJ2kConverterContext): Boolean {
        val root = applyToElement(treeRoot, initialState)
        assert(root === treeRoot)
        return somethingChanged
    }

    abstract fun applyToElement(element: JKTreeElement, state: S): JKTreeElement

    fun <T : JKTreeElement> recurse(element: T, state: S): T = applyRecursive(element, state, ::applyToElement)
}