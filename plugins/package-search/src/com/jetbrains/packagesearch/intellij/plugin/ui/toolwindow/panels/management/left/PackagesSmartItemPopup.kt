package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.CODE_OF_CONDUCT
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.GITHUB
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.README
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationUtility
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.asList
import icons.PackageSearchIcons
import java.awt.datatransfer.StringSelection
import javax.swing.JPopupMenu

class PackagesSmartItemPopup(viewModel: PackageSearchToolWindowModel, private val meta: PackageSearchDependency) :
    JPopupMenu() {

    private val packageOperationUtility = PackageOperationUtility(viewModel)

    init {
        // Build actions
        val selectedDependency = viewModel.searchResults.value[viewModel.selectedPackage.value]
        val selectedDependencyRemoteInfo = selectedDependency?.remoteInfo
        val selectedProjectModule = viewModel.selectedProjectModule.value
        val currentSelectedRemoteRepository = viewModel.selectedRemoteRepository.value
        val projectModules = if (selectedProjectModule != null) {
            // Selected module
            listOf(selectedProjectModule)
        } else {
            // All modules
            viewModel.projectModules.value
        }.filter {
            // Make sure the dependency is supported by the available project module(s)
            if (selectedDependencyRemoteInfo != null) {
                it.moduleType.providesSupportFor(selectedDependencyRemoteInfo)
            } else {
                true
            }
        }

        val packageOperationTargets = if (selectedDependency != null) {
            val preparedTargets = viewModel.preparePackageOperationTargetsFor(projectModules, selectedDependency, currentSelectedRemoteRepository)
            val installables = preparedTargets.filter { it.version.isBlank() }
            val updateables = preparedTargets.filter { it.version.isNotBlank() && !looksLikeGradleVariable(it.version) }
            if (updateables.isNotEmpty()) {
                updateables
            } else {
                installables
            }
        } else {
            emptyList()
        }

        // Build menu entries for actions
        val operationTargetVersion = meta.getLatestAvailableVersion(
            viewModel.selectedOnlyStable.value, currentSelectedRemoteRepository.asList()) ?: ""
        val operations = listOfNotNull(
            packageOperationUtility.getApplyOperation(packageOperationTargets, operationTargetVersion),
            packageOperationUtility.getRemoveOperation(packageOperationTargets)
        )
        operations.forEach { operation ->
            val projectsMessage = when (packageOperationTargets.size) {
                1 -> packageOperationTargets.first().projectModuleTitle
                else -> PackageSearchBundle.message("packagesearch.ui.toolwindow.selectedModules").toLowerCase()
            }

            @Suppress("HardCodedStringLiteral") // Formatting into a non-locale-specific format
            val message = operation.htmlDescription.replace("</html>", " - <b>$projectsMessage</b></html>")
            add(RiderUI.menuItem(message, operation.icon) {
                packageOperationUtility.doOperation(operation, packageOperationTargets, operationTargetVersion)
            })
        }
        if (operations.isNotEmpty()) {
            addSeparator()
        }

        // All links on package
        val links = meta.getAllLinks()

        // Encourage using Package Search website!
        links.remove(README)
        links.remove(CODE_OF_CONDUCT)
        add(RiderUI.menuItem(PackageSearchBundle.message("packagesearch.ui.popup.show.online"), PackageSearchIcons.Artifact) {
          BrowserUtil.browse(PackageSearchBundle.message("packagesearch.wellknown.url.jb.packagesearch.details", meta.identifier))
        })
        addSeparator()

        meta.remoteInfo?.gitHub?.let {
            val gitHubLink = links.remove(GITHUB) ?: return@let
            add(RiderUI.menuItem(PackageSearchBundle.message("packagesearch.ui.popup.open.github"), AllIcons.Vcs.Vendors.Github) {
                BrowserUtil.browse(gitHubLink)
            })
        }

        meta.remoteInfo?.gitHub?.communityProfile?.files?.license?.let {
            val licenseUrl = it.htmlUrl ?: it.url
            val licenseName = it.name ?: PackageSearchBundle.message("packagesearch.terminology.license.unknown")
            if (!licenseUrl.isNullOrEmpty()) {
                add(RiderUI.menuItem(PackageSearchBundle.message("packagesearch.ui.popup.open.license", licenseName), null) {
                    BrowserUtil.browse(licenseUrl)
                })
            }
        }

        links.forEach {
            add(RiderUI.menuItem(PackageSearchBundle.message("packagesearch.ui.popup.browse.thing", it.key.displayName), null) {
                BrowserUtil.browse(it.value)
            })
        }

        // Other entries
        addSeparator()
        add(RiderUI.menuItem(PackageSearchBundle.message("packagesearch.ui.popup.copy.identifier"), null) {
            CopyPasteManager.getInstance().setContents(StringSelection(meta.identifier))
        })
    }
}
