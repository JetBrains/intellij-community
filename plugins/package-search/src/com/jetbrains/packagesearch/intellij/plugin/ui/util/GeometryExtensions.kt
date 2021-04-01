package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Insets
import java.awt.Rectangle

@ScaledPixels
internal val Insets.horizontal: Int
    get() = left + right

@ScaledPixels
internal val Insets.vertical: Int
    get() = top + bottom

@ScaledPixels
internal val Rectangle.top: Int
    get() = y

@ScaledPixels
internal val Rectangle.left: Int
    get() = x

@ScaledPixels
internal val Rectangle.bottom: Int
    get() = y + height

@ScaledPixels
internal val Rectangle.right: Int
    get() = x + width
