// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.render

import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.sun.jdi.Type
import org.jetbrains.kotlin.idea.debugger.KotlinClassRenderer
import java.util.function.Function

private class KotlinClassRendererProvider : CompoundRendererProvider() {
    private val classRenderer = KotlinClassRenderer()

    override fun getName() = "Kotlin class"

    override fun getClassName() = "kotlin.Any?"

    override fun getValueLabelRenderer() = classRenderer

    override fun getChildrenRenderer() = classRenderer

    override fun getIsApplicableChecker() = Function { type: Type? -> classRenderer.isApplicableAsync(type) }

    override fun isEnabled() = true
}
