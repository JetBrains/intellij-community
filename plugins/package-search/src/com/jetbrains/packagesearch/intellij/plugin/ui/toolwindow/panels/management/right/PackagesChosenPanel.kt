package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationUtility
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.asList
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.localizedName
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import java.awt.Component
import javax.swing.Box
import javax.swing.JViewport

private val panelTitle = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.selectedPackage")

class PackagesChosenPanel(val viewModel: PackageSearchToolWindowModel) : PackageSearchPanelBase(panelTitle) {

    private val packageOperationUtility = PackageOperationUtility(viewModel)
    private val repoWarning = object : EditorNotificationPanel() {

        fun refreshUI(meta: PackageSearchDependency) {
            isVisible = false

            val firstMatchingRemoteRepository =
                viewModel.remoteRepositories.value.firstOrNull {
                    meta.remoteInfo?.versions?.any { version ->
                        !version.repositoryIds.isNullOrEmpty() && version.repositoryIds.contains(it.id)
                    } == true
                }

            if (firstMatchingRemoteRepository != null && !viewModel.repositories.value.any { it.remoteInfo == firstMatchingRemoteRepository }) {
                text(PackageSearchBundle.message("packagesearch.repository.willBeAddedOnInstall", firstMatchingRemoteRepository.localizedName()))
                isVisible = true
            }
        }
    }
    private val titleView = PackagesChosenTitleView()
    private val descriptionView = PackagesChosenDescriptionView()
    private val platformsView = PackagesChosenPlatformsView()
    private val versionView = PackagesChosenVersionView(viewModel)
    private val infoView = PackagesChosenInfoView()
    private val scopedControl = PackagesTargetModulesControl(viewModel)
    private val contentPanel = RiderUI.boxPanel {
        alignmentX = Component.LEFT_ALIGNMENT
        add(RiderUI.borderPanel {
            addToCenter(RiderUI.verticalScrollPane(RiderUI.borderPanel {
                border = JBUI.Borders.empty(0, 0, 0, 12)

                addToCenter(scopedControl)
                addToBottom(infoView.panel)
            }).apply {
                viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE // https://stackoverflow.com/a/54550638/95901

                UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)

                RiderUI.onMouseClicked(this) {
                    PackageSearchToolWindowFactory.activateToolWindow(viewModel.project)
                }
            })
        })
    }

    private var internalMode = false
    private val uiLock = Object()

    init {
        refreshUI()
        viewModel.selectedPackage.advise(viewModel.lifetime) { refreshUI() }
        viewModel.selectedProjectModule.advise(viewModel.lifetime) { refreshUI() }
        viewModel.selectedOnlyStable.advise(viewModel.lifetime) { refreshUI() }
        viewModel.isBusy.advise(viewModel.lifetime) { refreshHomogeneousButtons() }

        versionView.versionsComboBox.addActionListener {
            val selectedVersion = versionView.getSelectedVersion()
            scopedControl.targetVersion.set(selectedVersion)
            viewModel.targetPackageVersion.set(selectedVersion)
            refreshHomogeneousButtons()
        }

        RiderUI.onFocusGained(versionView.versionsComboBox) { scopedControl.clearSelection() }
    }

    private fun refreshUI() {
        synchronized(uiLock) {
            if (internalMode) {
                return
            }

            internalMode = true
            val targetPackageId = viewModel.selectedPackage.value
            if (targetPackageId != "") {
                val packages = viewModel.searchResults.value
                val meta = packages.getOrElse(targetPackageId, { createFakeMeta(targetPackageId) })

                descriptionView.show()
                platformsView.show()
                versionView.show()
                infoView.show()
                contentPanel.isVisible = true

                val availableVersions = meta.getAvailableVersions(
                    viewModel.selectedOnlyStable.value, viewModel.selectedRemoteRepository.value.asList()).toList()

                repoWarning.refreshUI(meta)
                titleView.refreshUI(meta)
                descriptionView.refreshUI(meta)
                platformsView.refreshUI(meta)
                versionView.refreshUI(availableVersions, meta.remoteInfo?.latestVersion?.version ?: availableVersions.firstOrNull())
                infoView.refreshUI(meta)
                scopedControl.refreshUI()
            } else {
                repoWarning.isVisible = false
                titleView.hide()
                descriptionView.hide()
                platformsView.hide()
                versionView.hide()
                infoView.hide()
                contentPanel.isVisible = false
            }
            internalMode = false
        }
    }

    private fun refreshHomogeneousButtons() {
        versionView.buttonPanel.apply {
            removeAll()
            val operations = arrayOf(
                scopedControl.getApplyPackageOperation(),
                scopedControl.getRemovePackageOperation()
            ).filterNotNull()

            val buttons = operations.map { operation ->
                operation.toButton { performOperation(it) }
            }

            val isEnabled = !viewModel.isBusy.value
            buttons.forEach {
                it.isEnabled = isEnabled
                add(it)
            }
        }
        versionView.panel.updateUI()
    }

    private fun performOperation(op: PackageOperation) {
        val projects = scopedControl.getPackageOperationTargets()
        packageOperationUtility.doOperation(op, projects, scopedControl.targetVersion.value)
    }

    private fun createFakeMeta(id: String) =
        PackageSearchDependency(id.substringBefore('/'), id.substringAfter('/'))

    @Suppress("MagicNumber") // Gotta love Swing APIs
    override fun build() = RiderUI.boxPanel {
        border = JBUI.Borders.empty(0, 0, 0, 0)

        add(repoWarning.apply {
            border = JBUI.Borders.empty(0, 20, 0, 20)
            RiderUI.setHeight(this, RiderUI.MediumHeaderHeight)
        })
        add(Box.createVerticalStrut(12))

        add(titleView.panel.apply {
            border = JBUI.Borders.empty(0, 12, 0, 12)

            RiderUI.onMouseClicked(this) {
                PackageSearchToolWindowFactory.activateToolWindow(viewModel.project)
            }
        })
        add(RiderUI.boxPanel {
            border = JBUI.Borders.empty(0, 12, 0, 0)

            add(descriptionView.panel)
            add(platformsView.panel)
            add(versionView.panel)
            add(contentPanel)
            add(Box.createVerticalGlue())
        })
    }
}
