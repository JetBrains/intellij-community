package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction

internal interface HasToolWindowActions {
    val gearActions: ActionGroup?
    val titleActions: Array<AnAction>?
}
