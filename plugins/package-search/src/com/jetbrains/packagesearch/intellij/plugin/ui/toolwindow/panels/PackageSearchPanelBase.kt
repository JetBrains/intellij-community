package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal abstract class PackageSearchPanelBase(@Nls val title: String) {

    val content: JComponent by lazy { build() }

    val toolbar: JComponent? by lazy { buildToolbar() }

    val topToolbar: JComponent? by lazy { buildTopToolbar() }

    val gearActions: ActionGroup? by lazy { buildGearActions() }

    val titleActions: Array<AnAction>? by lazy { buildTitleActions() }

    protected abstract fun build(): JComponent
    protected open fun buildToolbar(): JComponent? = null
    protected open fun buildTopToolbar(): JComponent? = null
    protected open fun buildGearActions(): ActionGroup? = null
    protected open fun buildTitleActions(): Array<AnAction>? = null
}
