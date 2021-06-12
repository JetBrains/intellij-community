package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.event.ActionEvent
import javax.swing.JButton

internal fun JButton.onClick(handler: (e: ActionEvent) -> Unit): (ActionEvent) -> Unit {
    val listener: (ActionEvent) -> Unit = { handler(it) }
    addActionListener(listener)
    return listener
}
