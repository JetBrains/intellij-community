package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.sun.jdi.Type
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class KotlinClassRendererProvider : CompoundRendererProvider() {
    private val classRenderer = KotlinClassRenderer()

    override fun getName() = "KotlinClass"

    override fun getClassName() = "kotlin.Any?"

    override fun getValueLabelRenderer() = classRenderer

    override fun getChildrenRenderer() = classRenderer

    override fun getIsApplicableChecker() =
        Function { type: Type? -> CompletableFuture.completedFuture(classRenderer.isApplicable(type)) }

    override fun isEnabled() = true
}
