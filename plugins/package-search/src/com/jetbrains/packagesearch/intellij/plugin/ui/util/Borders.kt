/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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
