package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

internal fun Component.onVisibilityChanged(action: (isVisible: Boolean) -> Unit) {
    addComponentListener(object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent?) {
            action(true)
        }

        override fun componentHidden(e: ComponentEvent?) {
            action(false)
        }
    })
}

internal fun Component.onOpacityChanged(action: (isOpaque: Boolean) -> Unit) {
    addPropertyChangeListener("opaque") {
        action(it.newValue as Boolean)
    }
}
