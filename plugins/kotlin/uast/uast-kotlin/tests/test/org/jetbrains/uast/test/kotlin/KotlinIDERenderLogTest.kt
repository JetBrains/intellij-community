// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.uast.UElement

class K1KotlinIDERenderLogTest: KotlinIDERenderLogTest() {
    override fun checkLeak(node: UElement) {
        checkDescriptorsLeak(node)
    }

    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
}

