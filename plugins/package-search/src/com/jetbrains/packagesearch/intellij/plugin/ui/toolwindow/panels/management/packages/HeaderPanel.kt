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
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scrollbarWidth
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel

@Suppress("MagicNumber") // Swing dimension constants
internal class HeaderPanel(
    onUpdateAllLinkClicked: (List<PackageSearchOperation<*>>) -> Unit
) : BorderLayoutPanel(), Displayable<HeaderPanel.ViewModel> {

    private val titleLabel = JLabel().apply {
        border = scaledEmptyBorder(right = 20)
        font = RelativeFont.BOLD.derive(font)
    }

    private val countLabel = JLabel().apply {
        foreground = PackageSearchUI.GRAY_COLOR
        border = scaledEmptyBorder(right = 8)
    }

    private val progressAnimation = AsyncProcessIcon("pkgs-header-progress").apply {
        isVisible = false
        suspend()
    }

    private val updateAllLink = HyperlinkLabel(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.text")
    ).apply {
        isVisible = false
        border = scaledEmptyBorder(top = 4)
        insets.top = 3.scaled()
    }

    private var updateAllOperations: List<PackageSearchOperation<*>> = emptyList()

    init {
        PackageSearchUI.setHeight(this, PackageSearchUI.SmallHeaderHeight)
        border = emptyBorder(top = 5.scaled(), left = 5.scaled(), right = 1.scaled() + scrollbarWidth())
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
            onUpdateAllLinkClicked(updateAllOperations)
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

    internal data class ViewModel(
        @Nls val title: String,
        val rowsCount: Int?,
        val availableUpdatesCount: Int,
        val updateOperations: List<PackageSearchOperation<*>>
    )

    override suspend fun display(viewModel: ViewModel) = withContext(Dispatchers.AppUI) {
        titleLabel.text = viewModel.title

        if (viewModel.rowsCount != null) {
            countLabel.isVisible = true
            countLabel.text = viewModel.rowsCount.toString()
        } else {
            countLabel.isVisible = false
        }

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
        border = emptyBorder(top = 5.scaled(), left = 5.scaled(), right = rightBorder)
        updateAndRepaint()
    }
}
