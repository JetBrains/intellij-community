package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.util.NlsContexts.TabTitle
import javax.swing.JComponent

abstract class PackageSearchPanelBase(@TabTitle val title: String) {

    private val _content = lazy { build() }
    val content: JComponent
        get() = _content.value

    private val _toolbar = lazy { buildToolbar() }
    val toolbar: JComponent?
        get() = _toolbar.value

    private val _topToolbar = lazy { buildTopToolbar() }
    val topToolbar: JComponent?
        get() = _topToolbar.value

    protected abstract fun build(): JComponent
    protected open fun buildToolbar(): JComponent? = null
    protected open fun buildTopToolbar(): JComponent? = null
}
