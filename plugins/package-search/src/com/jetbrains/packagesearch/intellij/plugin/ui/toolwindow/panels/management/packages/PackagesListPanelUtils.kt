package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.*
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.event.DocumentEvent

@Suppress("FunctionName")
internal fun SearchTextFieldTextChangedListener(action: (DocumentEvent) -> Unit) =
    object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = action(e)
    }

internal fun SearchTextField.addOnTextChangedListener(action: (String) -> Unit) =
    SearchTextFieldTextChangedListener { action(text) }.also { addDocumentListener(it) }

internal fun JCheckBox.addSelectionChangedListener(action: (Boolean) -> Unit) =
    addItemListener { e -> action(e.stateChange == ItemEvent.SELECTED) }

internal fun computeHeaderData(
    totalItemsCount: Int,
    packageUpdateInfos: List<PackagesTableItem.InstalledPackage>,
    hasSearchResults: Boolean,
    targetModules: TargetModules
): PackagesHeaderData {
    val moduleNames = if (targetModules is TargetModules.One) {
        targetModules.module.name
    } else {
        PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules").lowercase()
    }

    val labelText =
        if (hasSearchResults) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults")
        } else {
            PackageSearchBundle.message(
                "packagesearch.ui.toolwindow.tab.packages.installedPackages.addedIn",
                moduleNames
            )
        }
    return PackagesHeaderData(
        labelText = labelText,
        count = totalItemsCount.coerceAtLeast(0),
        availableUpdatesCount = packageUpdateInfos.count { it.uiPackageModel.packageOperations.hasUpgrade },
    ) {
        packageUpdateInfos.forEach { item ->
            item.uiPackageModel.packageOperations.primaryOperations(this)
        }
    }
}

internal fun computeSearchResultModels(
    searchResults: ProjectDataProvider.ParsedSearchResponse,
    installedPackages: List<UiPackageModel.Installed>,
    onlyStable: Boolean,
    targetModules: TargetModules,
    searchResultsUiStateOverrides: Map<PackageIdentifier, SearchResultUiState>,
    knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>>,
    project: Project,
    allKnownRepositories: List<RepositoryModel>
): List<UiPackageModel.SearchResult> {
    if (searchResults == null || searchResults.data.packages.isEmpty()) return emptyList()
    val installedCoordinates = installedPackages
        .map { "${it.packageModel.groupId}:${it.packageModel.artifactId}" }
        .toSet()
    val indexMap = searchResults.data.packages
        .mapIndexed { index, result -> "${result.groupId}:${result.artifactId}" to index }
        .toMap()
    return searchResults.data.packages
        .asSequence()
        .filterNot { "${it.groupId}:${it.artifactId}" in installedCoordinates }
        .map { PackageModel.SearchResult(it.groupId, it.artifactId, it, searchResults.parsedVersions.getValue(it)) }
        .map {
            it.toUiPackageModel(
                onlyStable = onlyStable,
                searchResultsUiStateOverrides = searchResultsUiStateOverrides,
                targetModules = targetModules,
                project = project,
                knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
                allKnownRepositories = allKnownRepositories
            )
        }
        .sortedBy { indexMap[it.identifier.rawValue] }
        .toList()
}

internal data class UiPackageModelCacheKey(
    val targetModules: TargetModules,
    val uiState: SearchResultUiState?,
    val onlyStable: Boolean,
    val searchResult: PackageModel.SearchResult
)
