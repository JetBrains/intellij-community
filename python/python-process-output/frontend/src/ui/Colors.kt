package com.intellij.python.processOutput.frontend.ui

import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified

internal object Colors {
    val ErrorText
        get() = retrieveColorOrUnspecified("Label.errorForeground")

    object Tree {
        val Info
            get() = retrieveColorOrUnspecified("Component.infoForeground")
    }

    object Output {
        val Info
            get() = retrieveColorOrUnspecified("Label.disabledForeground")
    }
}
