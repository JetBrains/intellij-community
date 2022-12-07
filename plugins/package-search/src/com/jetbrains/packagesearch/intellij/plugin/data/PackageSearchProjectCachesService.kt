package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesListPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.UiPackageModelCacheKey
import com.jetbrains.packagesearch.intellij.plugin.util.CoroutineLRUCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal class PackageSearchProjectCachesService(project: Project) {

    val searchCache: CoroutineLRUCache<PackagesListPanel.SearchCommandModel, ProjectDataProvider.ParsedSearchResponse> =
        CoroutineLRUCache(200)

    val searchPackageModelCache: CoroutineLRUCache<UiPackageModelCacheKey, UiPackageModel.SearchResult> =
        CoroutineLRUCache(1000)

    val installedDependencyCache: CoroutineLRUCache<InstalledDependency, ApiStandardPackage> =
        CoroutineLRUCache(500)

    val projectCacheDirectory = project.getProjectDataPath("pkgs")
        .also { if (!it.exists()) it.createDirectories() }

    suspend fun clear() = coroutineScope {
        launch { searchCache.clear() }
        launch { searchPackageModelCache.clear() }
        launch { installedDependencyCache.clear() }
        launch(Dispatchers.IO) {
            projectCacheDirectory.delete(true)
            projectCacheDirectory.createDirectories()
        }
    }
}