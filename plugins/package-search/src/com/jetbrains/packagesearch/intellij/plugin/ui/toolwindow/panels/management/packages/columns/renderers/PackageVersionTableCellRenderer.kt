package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBComboBoxLabel
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionViewModel
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
        val bgColor = if (!isSelected && value is VersionViewModel.InstallablePackage) {
            PackageSearchUI.ListRowHighlightBackground
        } else {
            background
        }

        background = bgColor

        add(
            JBComboBoxLabel().apply {
                table.colors.applyTo(this, isSelected)
                background = bgColor
                icon = AllIcons.General.LinkDropTriangle

                text = when (value) {
                    is VersionViewModel.InstalledPackage -> versionMessage(value.packageModel, onlyStable)
                    is VersionViewModel.InstallablePackage -> value.selectedVersion.displayName
                    else -> throw IllegalArgumentException("The value is expected to be a VersionViewModel, but wasn't.")
                }
            }
        )
    }

    @Nls
    private fun versionMessage(packageModel: PackageModel.Installed, onlyStable: Boolean): String {
        val installedVersions = packageModel.usageInfo.asSequence()
            .map { it.version }
            .distinct()
            .sorted()
            .joinToString { if (looksLikeGradleVariable(it)) "[${it.displayName}]" else it.displayName }

        require(installedVersions.isNotBlank()) { "An installed package cannot produce an empty installed versions list" }

        @Suppress("HardCodedStringLiteral") // Composed of @Nls components
        return buildString {
            append(installedVersions)

            if (packageModel.canBeUpgraded(onlyStable)) {
                val latestAvailableVersion = packageModel.getLatestAvailableVersion(onlyStable)
                    ?: return@buildString
                append(" → ")
                append(latestAvailableVersion.displayName)
            }
        }
    }
}
