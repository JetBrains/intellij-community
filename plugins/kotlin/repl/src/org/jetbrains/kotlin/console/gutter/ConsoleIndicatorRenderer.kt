// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console.gutter

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConsoleIndicatorRenderer(private val iconWithTooltip: IconWithTooltip) : GutterIconRenderer() {
    private val icon = iconWithTooltip.icon
    private val tooltip
        @NlsContexts.Tooltip
        get() = iconWithTooltip.tooltip

    override fun getIcon() = icon
    override fun getTooltipText() = tooltip

    override fun hashCode() = icon.hashCode()
    override fun equals(other: Any?) = icon == other.safeAs<ConsoleIndicatorRenderer>()?.icon
}