package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.util.ui.JBUI

/**
 * Creates an empty border with all the edges of size [size].
 *
 * @param size The size of the border. It will be scaled according to the DPI settings.
 * @see JBUI.Borders.empty
 */
internal fun emptyBorder(@ScalableUnits size: Int = 0) = emptyBorder(size, size, size, size)

/**
 * Creates an empty border with the vertical edges of size [vSize] and the horizontal edges of size [hSize].
 *
 * @param vSize The size of the vertical edges' border. It will be scaled according to the DPI settings.
 * @param hSize The size of the horizontal edges' border. It will be scaled according to the DPI settings.
 * @see JBUI.Borders.empty
 */
internal fun emptyBorder(@ScalableUnits vSize: Int = 0, @ScalableUnits hSize: Int = 0) = emptyBorder(vSize, hSize, vSize, hSize)

/**
 * Creates an empty border with the edges of size [left], [top], [right] and [bottom], respectively.
 *
 * @param left The size of the left edge's border. It will be scaled according to the DPI settings.
 * @param top The size of the top edge's border. It will be scaled according to the DPI settings.
 * @param right The size of the right edge's border. It will be scaled according to the DPI settings.
 * @param bottom The size of the bottom edge's border. It will be scaled according to the DPI settings.
 * @see JBUI.Borders.empty
 */
internal fun emptyBorder(
    @ScalableUnits top: Int = 0,
    @ScalableUnits left: Int = 0,
    @ScalableUnits bottom: Int = 0,
    @ScalableUnits right: Int = 0
) = JBUI.Borders.empty(top, left, bottom, right)
