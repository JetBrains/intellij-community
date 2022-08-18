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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import java.awt.Color
import javax.swing.JComponent
import javax.swing.JTable

internal data class TableColors(
    val selectionBackground: Color,
    val selectionForeground: Color,
    val background: Color,
    val foreground: Color
) {

    fun applyTo(component: JComponent, isSelected: Boolean) {
        component.background = if (isSelected) selectionBackground else background
        component.foreground = if (isSelected) selectionForeground else foreground
    }
}

internal val JTable.colors: TableColors
    get() = TableColors(selectionBackground, selectionForeground, background, foreground)
