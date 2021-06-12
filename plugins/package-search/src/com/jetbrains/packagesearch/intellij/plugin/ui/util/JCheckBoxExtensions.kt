package com.jetbrains.packagesearch.intellij.plugin.ui.util

import javax.swing.JCheckBox

internal fun JCheckBox.setSelectedIfDifferentFromCurrent(selected: Boolean) {
    if (isSelected != selected) isSelected = selected
}
