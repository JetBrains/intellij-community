package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.RelativeFont
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scrollbarWidth
import kotlinx.coroutines.Deferred
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel

@Suppress("MagicNumber") // Swing dimension constants
internal class HeaderPanel(
    onUpdateAllLinkClicked: (Deferred<List<PackageSearchOperation<*>>>) -> Unit
) : BorderLayoutPanel() {

    private val titleLabel = JLabel().apply {
        border = emptyBorder(right = 20)
        font = RelativeFont.BOLD.derive(font)
    }

    private val countLabel = JLabel().apply {
        foreground = PackageSearchUI.GRAY_COLOR
        border = emptyBorder(right = 8)
    }

    private val progressAnimation = AsyncProcessIcon("pkgs-header-progress").apply {
        isVisible = false
        suspend()
    }

    private val updateAllLink = HyperlinkLabel(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.text")
    ).apply {
        isVisible = false
        border = emptyBorder(top = 4)
        insets.top = 3.scaled()
    }

    private var updateAllOperations: Deferred<List<PackageSearchOperation<*>>>? = null

    init {
        PackageSearchUI.setHeightPreScaled(this, PackageSearchUI.SmallHeaderHeight.get())
        border = emptyBorder(top = 5, left = 5, right = 1 + scrollbarWidth())
        background = PackageSearchUI.SectionHeaderBackgroundColor

        add(
            PackageSearchUI.flowPanel(PackageSearchUI.SectionHeaderBackgroundColor) {
                layout = FlowLayout(FlowLayout.LEFT, 6.scaled(), 0)

                add(titleLabel)
                add(countLabel)
                add(progressAnimation)
            },
            BorderLayout.WEST
        )

        add(
            PackageSearchUI.flowPanel(PackageSearchUI.SectionHeaderBackgroundColor) {
                layout = FlowLayout(FlowLayout.RIGHT, 6.scaled(), 0)
                add(updateAllLink)
            },
            BorderLayout.EAST
        )

        updateAllLink.addHyperlinkListener {
            updateAllOperations?.let { onUpdateAllLinkClicked(it) }
            PackageSearchEventsLogger.logUpgradeAll()
        }
    }

    fun showBusyIndicator(showIndicator: Boolean) {
        if (showIndicator) {
            progressAnimation.isVisible = true
            progressAnimation.resume()
        } else {
            progressAnimation.isVisible = false
            progressAnimation.suspend()
        }
    }

    fun display(viewModel: PackagesHeaderData) {
        titleLabel.text = viewModel.labelText

        countLabel.isVisible = true
        countLabel.text = viewModel.count.toString()

        updateAllOperations = viewModel.updateOperations
        if (viewModel.availableUpdatesCount > 0) {
            updateAllLink.setHyperlinkText(
                PackageSearchBundle.message(
                    "packagesearch.ui.toolwindow.actions.upgradeAll.text.withCount",
                    viewModel.availableUpdatesCount
                )
            )
            updateAllLink.isVisible = true
        } else {
            updateAllLink.isVisible = false
        }
    }

    fun adjustForScrollbar(scrollbarVisible: Boolean, scrollbarOpaque: Boolean) {
        // Non-opaque scrollbars for JTable are supported on Mac only (as of IJ 2020.3):
        // See JBScrollPane.Layout#isAlwaysOpaque
        val isAlwaysOpaque = !SystemInfo.isMac /* && ScrollSettings.isNotSupportedYet(view), == true for JTable */
        val includeScrollbar = scrollbarVisible && (isAlwaysOpaque || scrollbarOpaque)

        @ScaledPixels val rightBorder = if (includeScrollbar) scrollbarWidth() else 1.scaled()
        border = emptyBorder(top = 5, left = 5, right = rightBorder)
        updateAndRepaint()
    }
}
