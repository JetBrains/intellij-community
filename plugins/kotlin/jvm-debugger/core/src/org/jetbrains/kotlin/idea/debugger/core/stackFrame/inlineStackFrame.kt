// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.xdebugger.impl.frame.XStackFrameWithCustomBackgroundColor
import com.intellij.xdebugger.ui.DebuggerColors
import com.sun.jdi.Location
import java.awt.Color

class InlineStackFrame(
    location: Location?,
    name: String,
    frameProxy: StackFrameProxyImpl,
    variableInlineDepth: Int,
    visibleVariables: List<LocalVariableProxyImpl>,
    inlineScopeNumber: Int = -1,
    surroundingScopeNumber: Int = -1,
) : KotlinStackFrame(
        safeInlineStackFrameProxy(
            location, variableInlineDepth, frameProxy, inlineScopeNumber, surroundingScopeNumber
        ),
        visibleVariables
    ), XStackFrameWithCustomBackgroundColor {
    init {
        descriptor.name = name
        descriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER)
    }

    private val inlineDepth: Int?
        get() = (descriptor.frameProxy as? InlineStackFrameProxyImpl)?.inlineDepth

    override fun getBackgroundColor(): Color? =
        EditorColorsManager.getInstance()
            .schemeForCurrentUITheme
            .getAttributes(DebuggerColors.INLINE_STACK_FRAMES)
            .backgroundColor

    override fun toString(): String {
        val mainString = super.toString()
        val inlineDepth = inlineDepth ?: return mainString
        return "$mainString (inline depth = $inlineDepth)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is InlineStackFrame) {
            return false
        }
        if (!super.equals(other)) {
            return false
        }
        return inlineDepth == other.inlineDepth
                && descriptor.location == other.descriptor.location
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + inlineDepth.hashCode()
        return result
    }
}
