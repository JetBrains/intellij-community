// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement


abstract class RecursiveApplicableConversionWithState<S>(
    context: NewJ2kConverterContext,
    private val initialState: S
)  : MatchBasedConversion(context) {
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
