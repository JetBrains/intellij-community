package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Color

internal fun Color.toCssHexColorString() = String.format("#%02x%02x%02x", red, green, blue)
