// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement


abstract class RecursiveApplicableConversionBase(context: NewJ2kConverterContext) : MatchBasedConversion(context) {
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

val RecursiveApplicableConversionBase.moduleApiVersion: ApiVersion
    get() {
        val converter = context.converter
        val languageVersionSettings = converter.targetModule?.languageVersionSettings ?: converter.project.languageVersionSettings
        return languageVersionSettings.apiVersion
    }