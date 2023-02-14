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

import java.awt.Component
import javax.swing.JPopupMenu
import javax.swing.SwingConstants

internal fun JPopupMenu.showUnderneath(target: Component? = invoker, alignEdge: Int = SwingConstants.LEFT) {
    require(target != null) { "The popup menu must be anchored to an invoker, or have a non-null target" }

    val x = when (alignEdge) {
        SwingConstants.LEFT -> 0
        SwingConstants.RIGHT -> target.width - width
        else -> throw IllegalArgumentException("Only SwingConstants.LEFT and SwingConstants.RIGHT alignments are supported")
    }
    show(target, x, target.height)
}
