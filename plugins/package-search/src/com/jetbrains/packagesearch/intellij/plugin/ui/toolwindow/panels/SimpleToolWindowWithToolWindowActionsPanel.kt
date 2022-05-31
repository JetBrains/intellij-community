package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.ui.SimpleToolWindowPanel

internal class SimpleToolWindowWithToolWindowActionsPanel(
    override val gearActions: ActionGroup?,
    override val titleActions: Array<AnAction>?,
    vertical: Boolean = false,
    borderless: Boolean = false,
    val provider: DataProvider
) : SimpleToolWindowPanel(vertical, borderless), HasToolWindowActions, DataProvider {

    override fun getData(dataId: String) = provider.getData(dataId)
}
