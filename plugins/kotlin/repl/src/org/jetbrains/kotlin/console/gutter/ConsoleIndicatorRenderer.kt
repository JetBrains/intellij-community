// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console.gutter

import com.intellij.openapi.editor.markup.GutterIconRenderer

class ConsoleIndicatorRenderer(iconWithTooltip: IconWithTooltip) : GutterIconRenderer() {
    private val icon = iconWithTooltip.icon
    private val tooltip = iconWithTooltip.tooltip

    override fun getIcon() = icon
    override fun getTooltipText() = tooltip

    override fun hashCode() = icon.hashCode()
    override fun equals(other: Any?) = icon == (other as? ConsoleIndicatorRenderer)?.icon
}