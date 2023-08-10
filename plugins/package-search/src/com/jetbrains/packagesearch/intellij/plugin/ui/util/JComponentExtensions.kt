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

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal fun JComponent.onRightClick(
    onRightClick: (e: MouseEvent) -> Unit = {}
) = onMouseAction(
    onClick = {
        if (SwingUtilities.isRightMouseButton(it)) onRightClick(it)
    }
)

internal fun JComponent.onMouseAction(
    onClick: (e: MouseEvent) -> Unit = {},
    onPressed: (e: MouseEvent) -> Unit = {},
    onReleased: (e: MouseEvent) -> Unit = {},
    onEntered: (e: MouseEvent) -> Unit = {},
    onExited: (e: MouseEvent) -> Unit = {}
): MouseListener {
    val listener = mouseListener(onClick, onPressed, onReleased, onEntered, onExited)
    addMouseListener(listener)
    return listener
}

@ScaledPixels
internal val JComponent.left: Int
    get() = x

@ScaledPixels
internal val JComponent.top: Int
    get() = y

@ScaledPixels
internal val JComponent.bottom: Int
    get() = y + height

@ScaledPixels
internal val JComponent.right: Int
    get() = x + width

@ScaledPixels
internal val JComponent.verticalCenter: Int
    get() = y + height / 2

