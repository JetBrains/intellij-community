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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.TableHoverListener
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperations
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class PackageVersionTableCellRenderer : TableCellRenderer {

    private var onlyStable = false

    fun updateData(onlyStable: Boolean) {
        this.onlyStable = onlyStable
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = JPanel(MigLayout("al left center, insets 0 8 0 0")).apply {
        val isHover = TableHoverListener.getHoveredRow(table) == row
        val isSearchResult = value is UiPackageModel.SearchResult

        val colors = computeColors(isSelected, isHover, isSearchResult)
        colors.applyTo(this)

        val viewModel = checkNotNull(value as? UiPackageModel<*>)
        val labelText = when (viewModel) {
            is UiPackageModel.Installed -> versionMessage(viewModel.packageModel, viewModel.packageOperations)
            is UiPackageModel.SearchResult -> viewModel.selectedVersion.displayName
        }

        val hasVersionsToChooseFrom = viewModel.sortedVersions.isNotEmpty()
        val labelComponent: JComponent = if (hasVersionsToChooseFrom) {
            JBComboBoxLabel().apply {
                icon = AllIcons.General.LinkDropTriangle
                text = labelText
            }
        } else {
            JBLabel().apply {
                text = labelText
            }
        }.apply {
            background = colors.background
            foreground = colors.foreground
        }

        add(labelComponent)
    }

    @Nls
    private fun versionMessage(packageModel: PackageModel.Installed, packageOperations: PackageOperations): String {
        val installedVersions = packageModel.usageInfo.asSequence()
            .map { it.declaredVersion }
            .distinct()
            .sorted()
            .joinToString { if (looksLikeGradleVariable(it)) "[${it.displayName}]" else it.displayName }

        require(installedVersions.isNotBlank()) { "An installed package cannot produce an empty installed versions list" }

        @Suppress("HardCodedStringLiteral") // Composed of @Nls components
        return buildString {
            append(installedVersions)

            if (packageOperations.canUpgradePackage) {
                val upgradeVersion = packageOperations.targetVersion ?: return@buildString
                append(" → ")
                append(upgradeVersion.displayName)
            }
        }
    }
}
