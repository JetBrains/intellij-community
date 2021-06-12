package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.SimpleToolWindowPanel

internal class SimpleToolWindowWithToolWindowActionsPanel(
    override val gearActions: ActionGroup?,
    override val titleActions: Array<AnAction>?,
    vertical: Boolean = false,
    borderless: Boolean = false
) : SimpleToolWindowPanel(vertical, borderless), HasToolWindowActions
