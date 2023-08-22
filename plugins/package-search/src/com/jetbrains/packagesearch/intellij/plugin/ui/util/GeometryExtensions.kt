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
