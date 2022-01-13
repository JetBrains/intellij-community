package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.util.ui.JBEmptyBorder

internal fun emptyBorder(@ScaledPixels size: Int = 0) = emptyBorder(size, size, size, size)

internal fun emptyBorder(@ScaledPixels vSize: Int = 0, @ScaledPixels hSize: Int = 0) = emptyBorder(vSize, hSize, vSize, hSize)

internal fun emptyBorder(
    @ScaledPixels top: Int = 0,
    @ScaledPixels left: Int = 0,
    @ScaledPixels bottom: Int = 0,
    @ScaledPixels right: Int = 0
) = JBEmptyBorder(top, left, bottom, right)
