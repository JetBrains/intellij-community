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
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2

internal abstract class AbstractLayoutManager2 : LayoutManager2 {

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
        throw UnsupportedOperationException()
    }

    override fun addLayoutComponent(name: String, comp: Component) {
        throw UnsupportedOperationException()
    }

    override fun removeLayoutComponent(comp: Component) {
        throw UnsupportedOperationException()
    }

    override fun preferredLayoutSize(parent: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun minimumLayoutSize(parent: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun layoutContainer(parent: Container) {
        throw UnsupportedOperationException()
    }

    override fun maximumLayoutSize(target: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun getLayoutAlignmentX(target: Container): Float {
        throw UnsupportedOperationException()
    }

    override fun getLayoutAlignmentY(target: Container): Float {
        throw UnsupportedOperationException()
    }

    override fun invalidateLayout(target: Container) {
        throw UnsupportedOperationException()
    }
}
