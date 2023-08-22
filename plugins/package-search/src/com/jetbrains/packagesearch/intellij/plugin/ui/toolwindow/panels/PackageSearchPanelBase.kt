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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal abstract class PackageSearchPanelBase(@Nls val title: String) : DataProvider {

    internal val content: JComponent by lazy { build() }

    internal val toolbar: JComponent? by lazy { buildToolbar() }

    internal val topToolbar: JComponent? by lazy { buildTopToolbar() }

    internal val gearActions: ActionGroup? by lazy { buildGearActions() }

    internal val titleActions: List<AnAction> by lazy(::buildTitleActions)

    protected abstract fun build(): JComponent
    protected open fun buildToolbar(): JComponent? = null
    protected open fun buildTopToolbar(): JComponent? = null
    protected open fun buildGearActions(): ActionGroup? = null
    protected open fun buildTitleActions(): List<AnAction> = emptyList()
}
