package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.ColorButton
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.util.AbstractLayoutManager2
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.HtmlEditorPane
import com.jetbrains.packagesearch.intellij.plugin.ui.util.MenuAction
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.bottom
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.horizontal
import com.jetbrains.packagesearch.intellij.plugin.ui.util.left
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onRightClick
import com.jetbrains.packagesearch.intellij.plugin.ui.util.right
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledFontSize
import com.jetbrains.packagesearch.intellij.plugin.ui.util.showUnderneath
import com.jetbrains.packagesearch.intellij.plugin.ui.util.top
import com.jetbrains.packagesearch.intellij.plugin.ui.util.vertical
import com.jetbrains.packagesearch.intellij.plugin.ui.util.verticalCenter
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.JTextComponent
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

private val minPopupMenuWidth = 175.scaled()

internal class PackageDetailsHeaderPanel(
    private val operationFactory: PackageSearchOperationFactory,
    private val operationExecutor: OperationExecutor
) : JPanel(), Displayable<PackageDetailsHeaderPanel.ViewModel> {

    private val repoWarningBanner = InfoBannerPanel().apply {
        isVisible = false
    }

    private val nameLabel = HtmlEditorPane().apply {
        border = emptyBorder()
        font = JBFont.label().asBold().deriveFont(16.scaledFontSize())
    }

    private val identifierLabel = HtmlEditorPane().apply {
        border = emptyBorder()
        foreground = PackageSearchUI.getTextColorSecondary()
    }

    private val primaryActionButton = ColorButton().apply {
        addActionListener { onPrimaryActionClicked() }
    }

    private var primaryOperations: List<PackageSearchOperation<*>> = emptyList()

    private val removeMenuAction = MenuAction().apply {
        add(object : DumbAwareAction(PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.remove.text")) {

            init {
                minimumSize = Dimension(minPopupMenuWidth, 0)
            }

            override fun actionPerformed(e: AnActionEvent) {
                onRemoveClicked()
            }
        })
    }

    private val overflowButton = run {
        val presentation = Presentation()
        presentation.icon = AllIcons.Actions.More
        presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)

        ActionButton(removeMenuAction, presentation, "PackageSearchPackageDetailsHeader", ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE)
    }

    private var removeOperations: List<PackageSearchOperation<*>> = emptyList()

    private val copyMenuItem = PackageSearchUI.menuItem(
        title = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.menu.copy"),
        icon = null,
        handler = ::onCopyClicked
    ).apply {
        mnemonic = KeyEvent.VK_C
        minimumSize = Dimension(minPopupMenuWidth, 0)
    }

    private val copyMenu = JBPopupMenu().apply {
        minimumSize = Dimension(minPopupMenuWidth, 0)
        add(copyMenuItem)
    }

    private val infoPanel = PackageSearchUI.headerPanel {
        border = emptyBorder(12.scaled())
        layout = HeaderLayout()

        add(nameLabel, HeaderLayout.Role.NAME)
        add(primaryActionButton, HeaderLayout.Role.PRIMARY_ACTION)
        add(overflowButton, HeaderLayout.Role.OVERFLOW_BUTTON)
        add(identifierLabel, HeaderLayout.Role.IDENTIFIER)
    }

    init {
        layout = BorderLayout()

        border = JBEmptyBorder(0)

        add(repoWarningBanner, BorderLayout.NORTH)
        add(infoPanel, BorderLayout.CENTER)

        UIUtil.enableEagerSoftWrapping(nameLabel)
        UIUtil.enableEagerSoftWrapping(identifierLabel)

        nameLabel.onRightClick { if (nameLabel.isVisible) copyMenu.showUnderneath(nameLabel) }
        identifierLabel.onRightClick { if (identifierLabel.isVisible) copyMenu.showUnderneath(identifierLabel) }
    }

    internal data class ViewModel(
        val selectedPackageModel: SelectedPackageModel<*>,
        val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        val allKnownRepositories: KnownRepositories.All,
        val targetModules: TargetModules,
        val onlyStable: Boolean
    )

    override suspend fun display(viewModel: ViewModel) = withContext(Dispatchers.AppUI) {
        val packageModel = viewModel.selectedPackageModel.packageModel

        val name = packageModel.remoteInfo?.name
        if (name != null && name != packageModel.identifier) {
            @Suppress("HardCodedStringLiteral") // Name comes from the API
            nameLabel.setBody(
                listOf(
                    HtmlChunk.span("font-size: ${16.scaledFontSize()};")
                        .addRaw("<b>" + StringUtils.normalizeSpace(packageModel.remoteInfo.name) + "</b>")
                )
            )
            identifierLabel.setBodyText(packageModel.identifier)
            identifierLabel.isVisible = true
        } else {
            nameLabel.setBodyText(packageModel.identifier)
            identifierLabel.text = null
            identifierLabel.isVisible = false
        }

        val selectedVersion = viewModel.selectedPackageModel.selectedVersion
        val selectedScope = viewModel.selectedPackageModel.selectedScope
        val repoToInstall = viewModel.knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
            packageModel,
            selectedVersion,
            viewModel.allKnownRepositories
        )

        updateRepoWarningBanner(repoToInstall)
        updateOperations(packageModel, selectedVersion, selectedScope, viewModel.targetModules, viewModel.onlyStable, repoToInstall)

        overflowButton.componentPopupMenu?.isVisible = false
    }

    private fun updateRepoWarningBanner(repoToInstall: RepositoryModel?) {
        if (repoToInstall == null) {
            repoWarningBanner.isVisible = false
        } else {
            repoWarningBanner.text = PackageSearchBundle.message(
                "packagesearch.repository.willBeAddedOnInstall",
                repoToInstall.displayName
            )
            repoWarningBanner.isVisible = true
        }
    }

    private fun updateOperations(
        packageModel: PackageModel,
        selectedVersion: PackageVersion,
        selectedScope: PackageScope,
        targetModules: TargetModules,
        onlyStable: Boolean,
        repoToInstall: RepositoryModel?
    ) {
        when (packageModel) {
            is PackageModel.Installed -> {
                setupActionsForInstalledPackage(packageModel, selectedVersion, selectedScope, targetModules, onlyStable, repoToInstall)
            }
            is PackageModel.SearchResult -> {
                primaryActionButton.isVisible = true
                primaryActionButton.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text")
                primaryOperations = operationFactory.createAddPackageOperations(
                    packageModel = packageModel,
                    version = selectedVersion,
                    scope = selectedScope,
                    targetModules = targetModules,
                    repoToInstall = repoToInstall
                )

                overflowButton.isVisible = false
                removeOperations = emptyList()
            }
        }
    }

    private fun setupActionsForInstalledPackage(
        packageModel: PackageModel.Installed,
        selectedVersion: PackageVersion,
        selectedScope: PackageScope,
        targetModules: TargetModules,
        onlyStable: Boolean,
        repoToInstall: RepositoryModel?
    ) {
        overflowButton.isVisible = true

        when {
            selectedVersion == PackageVersion.Missing || packageModel.canBeUpgraded(selectedVersion, onlyStable) -> {
                showUpgradePrimaryAction(packageModel, selectedVersion, selectedScope, targetModules, repoToInstall)
            }
            packageModel.canBeDowngraded(selectedVersion, onlyStable) -> {
                primaryOperations = operationFactory.createChangePackageOperations(
                    packageModel = packageModel,
                    newVersion = selectedVersion,
                    newScope = selectedScope,
                    targetModules = targetModules,
                    repoToInstall = repoToInstall
                )
                primaryActionButton.isVisible = true
                primaryActionButton.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.downgrade.text")
            }
            else -> {
                primaryOperations = emptyList()
                primaryActionButton.isVisible = false
            }
        }

        removeOperations = operationFactory.createRemovePackageOperations(
            packageModel = packageModel,
            version = selectedVersion,
            scope = selectedScope,
            targetModules = targetModules
        )
    }

    private fun showUpgradePrimaryAction(
        packageModel: PackageModel.Installed,
        selectedVersion: PackageVersion,
        selectedScope: PackageScope,
        targetModules: TargetModules,
        repoToInstall: RepositoryModel?
    ) {
        primaryOperations = operationFactory.createChangePackageOperations(
            packageModel = packageModel,
            newVersion = selectedVersion,
            newScope = selectedScope,
            targetModules = targetModules,
            repoToInstall = repoToInstall
        )
        primaryActionButton.isVisible = true
        primaryActionButton.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgrade.text")
    }

    private fun onPrimaryActionClicked() {
        if (primaryOperations.isEmpty()) {
            logger<PackageDetailsHeaderPanel>().error("No primary action operations to perform, status mismatch")
            return
        }
        operationExecutor.executeOperations(primaryOperations)
    }

    private fun onRemoveClicked() {
        if (removeOperations.isEmpty()) {
            logger<PackageDetailsHeaderPanel>().error("No remove operations to perform, status mismatch")
            return
        }
        operationExecutor.executeOperations(removeOperations)
    }

    private fun onCopyClicked() {
        val text = (copyMenu.invoker as? JTextComponent)?.text?.nullIfBlank()
            ?: return

        CopyPasteManager.getInstance()
            .setContents(StringSelection(text.stripHtml()))
    }

    private fun String.stripHtml(): String {
        val stringBuilder = StringBuilder()

        val parserDelegator = ParserDelegator()
        val parserCallback: HTMLEditorKit.ParserCallback = object : HTMLEditorKit.ParserCallback() {
            override fun handleText(data: CharArray, pos: Int) {
                stringBuilder.append(data)
            }
        }
        parserDelegator.parse(this.reader(), parserCallback, true)
        return stringBuilder.toString()
    }
}

private class HeaderLayout : AbstractLayoutManager2() {

    @ScaledPixels
    private val overflowButtonSize = 20.scaled()

    @ScaledPixels
    private val gapBetweenPrimaryActionAndOverflow = 2.scaled()

    @ScaledPixels
    private val primaryActionButtonHeight = 26.scaled()

    @ScaledPixels
    private val gapBetweenNameAndButtons = 4.scaled()

    @ScaledPixels
    private val vGapBetweenNameAndIdentifier = 4.scaled()

    private val componentByRole = mutableMapOf<Role, JComponent>()
    private var dirty = true

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
        if (constraints !is Role) throw UnsupportedOperationException("A Role must be provided for the component")
        componentByRole[constraints] = comp as JComponent
    }

    override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)

    override fun preferredLayoutSize(parent: Container): Dimension {
        layoutContainer(parent) // Re-layout if needed

        val identifierLabel = componentByRole[Role.IDENTIFIER]?.bottom ?: error("Identifier label missing")
        return Dimension(
            parent.width - parent.insets.horizontal,
            identifierLabel + parent.insets.vertical
        )
    }

    override fun maximumLayoutSize(target: Container) =
        target.maximumSize ?: Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    @Suppress("ComplexMethod", "LongMethod") // Such is the life of a Swing layout manager...
    override fun layoutContainer(parent: Container) {
        synchronized(parent.treeLock) {
            if (!dirty) return
            if (parent.width <= 0) return

            val insets = parent.insets
            val bounds = Rectangle(insets.left, insets.top, parent.width - insets.horizontal, parent.height - insets.vertical)

            val overflowButton = componentByRole[Role.OVERFLOW_BUTTON] ?: error("Overflow button missing")
            val overflowButtonWidth = if (overflowButton.isVisible) overflowButtonSize else 0
            overflowButton.setBounds(
                bounds.right - overflowButtonSize,
                bounds.top + primaryActionButtonHeight / 2 - overflowButtonSize / 2,
                overflowButtonWidth,
                overflowButtonSize
            )

            val primaryActionButton = componentByRole[Role.PRIMARY_ACTION] ?: error("Primary action button missing")
            val primaryActionWidth = if (primaryActionButton.isVisible) primaryActionButton.preferredSize.width else 0
            val buttonsGap = if (primaryActionButton.isVisible && overflowButton.isVisible) gapBetweenPrimaryActionAndOverflow else 0
            primaryActionButton.setBounds(
                overflowButton.left - buttonsGap - primaryActionWidth,
                bounds.top,
                primaryActionWidth,
                primaryActionButtonHeight
            )

            val nameLabel = componentByRole[Role.NAME] ?: error("Name label missing")
            val nameLabelHeight = nameLabel.preferredSize.height
            val nameLabelButtonGap = if (primaryActionButton.isVisible || overflowButton.isVisible) gapBetweenNameAndButtons else 0
            val nameLabelWidth = primaryActionButton.left - bounds.left - nameLabelButtonGap

            if (nameLabelHeight >= primaryActionButtonHeight) {
                nameLabel.setBounds(bounds.left, bounds.top, nameLabelWidth, nameLabelHeight)
            } else {
                nameLabel.setBounds(
                    bounds.left,
                    primaryActionButton.verticalCenter - nameLabelHeight / 2, // Center vertically on primary action
                    nameLabelWidth,
                    nameLabelHeight
                )
            }

            val identifierLabel = componentByRole[Role.IDENTIFIER] ?: error("Identifier label missing")
            val labelsY = maxOf(nameLabel.bottom + vGapBetweenNameAndIdentifier, primaryActionButton.bottom + vGapBetweenNameAndIdentifier)
            if (identifierLabel.isVisible) {
                identifierLabel.setBounds(
                    bounds.left,
                    labelsY,
                    bounds.width,
                    if (identifierLabel.isVisible) identifierLabel.preferredSize.height else 0
                )
            } else {
                identifierLabel.setBounds(0, nameLabel.bottom, bounds.width, 0)
            }

            dirty = false
        }
    }

    override fun getLayoutAlignmentX(target: Container) = 0.0F

    override fun getLayoutAlignmentY(target: Container) = 0.0F

    override fun invalidateLayout(target: Container) {
        dirty = true
    }

    enum class Role {
        NAME,
        PRIMARY_ACTION,
        OVERFLOW_BUTTON,
        IDENTIFIER
    }
}
