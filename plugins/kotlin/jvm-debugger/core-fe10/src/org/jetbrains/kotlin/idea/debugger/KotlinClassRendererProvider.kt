// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.sun.jdi.Type
import java.util.function.Function

class KotlinClassRendererProvider : CompoundRendererProvider() {
    private val classRenderer = KotlinClassRenderer()

    override fun getName() = "Kotlin class"

    override fun getClassName() = "kotlin.Any?"

    override fun getValueLabelRenderer() = classRenderer

    override fun getChildrenRenderer() = classRenderer

    override fun getIsApplicableChecker() = Function { type: Type? -> classRenderer.isApplicableAsync(type) }

    override fun isEnabled() = true
}
