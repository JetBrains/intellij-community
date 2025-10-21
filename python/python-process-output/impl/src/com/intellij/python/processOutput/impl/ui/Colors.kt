package com.intellij.python.processOutput.impl.ui

import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified

internal object Colors {
    object Tree {
        val Selected
            get() = retrieveColorOrUnspecified("List.selectionBackground")

        val Hovered
            get() = retrieveColorOrUnspecified("ColorPalette.Gray3")

        val Info
            get() = retrieveColorOrUnspecified("Component.infoForeground")
    }

    object Output {
        val ErrorText
            get() = retrieveColorOrUnspecified("Label.errorForeground")

        val Info
            get() = retrieveColorOrUnspecified("Label.disabledForeground")
    }
}
