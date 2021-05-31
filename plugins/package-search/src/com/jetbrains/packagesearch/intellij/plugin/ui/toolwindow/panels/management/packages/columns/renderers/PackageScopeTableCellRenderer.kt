package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBComboBoxLabel
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeViewModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal object PackageScopeTableCellRenderer : TableCellRenderer {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = JPanel(MigLayout("al left center, insets 0 8 0 0")).apply {
        table.colors.applyTo(this, isSelected)

        val bgColor = if (!isSelected && value is ScopeViewModel.InstallablePackage) {
            PackageSearchUI.ListRowHighlightBackground
        } else {
            background
        }

        background = bgColor

        val jbComboBoxLabel = JBComboBoxLabel().apply {
            table.colors.applyTo(this, isSelected)
            background = bgColor
            icon = AllIcons.General.LinkDropTriangle

            text = when (value) {
                is ScopeViewModel.InstalledPackage -> scopesMessage(value.installedScopes, value.defaultScope)
                is ScopeViewModel.InstallablePackage -> value.selectedScope.displayName
                else -> throw IllegalArgumentException("The value is expected to be a ScopeViewModel, but wasn't.")
            }
        }
        add(jbComboBoxLabel)
    }

    @NlsSafe
    private fun scopesMessage(installedScopes: List<PackageScope>, defaultScope: PackageScope): String {
        if (installedScopes.isEmpty()) return defaultScope.displayName

        return installedScopes.joinToString { it.displayName }
    }
}
