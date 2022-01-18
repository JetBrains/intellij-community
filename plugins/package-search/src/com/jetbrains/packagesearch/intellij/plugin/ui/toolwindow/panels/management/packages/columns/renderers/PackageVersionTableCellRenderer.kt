package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.JBLabel
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperations
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
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
        table.colors.applyTo(this, isSelected)

        val bgColor = if (!isSelected && value is UiPackageModel.SearchResult) {
            PackageSearchUI.ListRowHighlightBackground
        } else {
            background
        }

        background = bgColor

        val viewModel = checkNotNull(value as? UiPackageModel<*>)
        val labelText = when (viewModel) {
            is UiPackageModel.Installed -> versionMessage(viewModel.packageModel, viewModel.packageOperations)
            is UiPackageModel.SearchResult -> viewModel.selectedVersion.displayName
        }

        val hasVersionsToChooseFrom = viewModel.sortedVersions.isNotEmpty()
        val labelComponent = if (hasVersionsToChooseFrom) {
            JBComboBoxLabel().apply {
                icon = AllIcons.General.LinkDropTriangle
                text = labelText
            }
        } else {
            JBLabel().apply { text = labelText }
        }

        add(
            labelComponent.apply {
                table.colors.applyTo(this, isSelected)
                background = bgColor
            }
        )
    }

    @Nls
    private fun versionMessage(packageModel: PackageModel.Installed, packageOperations: PackageOperations): String {
        val installedVersions = packageModel.usageInfo.asSequence()
            .map { it.version }
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
