package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Insets

internal fun scaledInsets(
    @ScalableUnits top: Int = 0,
    @ScalableUnits left: Int = 0,
    @ScalableUnits bottom: Int = 0,
    @ScalableUnits right: Int = 0
) = Insets(top.scaled(), left.scaled(), bottom.scaled(), right.scaled())

internal fun insets(
    @ScaledPixels top: Int = 0,
    @ScaledPixels left: Int = 0,
    @ScaledPixels bottom: Int = 0,
    @ScaledPixels right: Int = 0
) = Insets(top, left, bottom, right)
