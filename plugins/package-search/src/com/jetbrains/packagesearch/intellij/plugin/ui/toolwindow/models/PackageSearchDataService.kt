package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationGroupManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiRepository
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.api.http.ApiResult
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration
import com.jetbrains.packagesearch.intellij.plugin.extensions.maven.MavenProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.OperationFailureRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.ui.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.debounce
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.rd.util.getOrCreate
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls
import java.net.URI
import java.util.Locale

private val MAVEN_CENTRAL_UNIFIED_REPOSITORY =
    UnifiedDependencyRepository("central", "Central Repository", "https://repo.maven.apache.org/maven2")

private const val API_TIMEOUT_MILLIS = 10_000L
private const val DATA_DEBOUNCE_MILLIS = 100L
private const val SEARCH_DEBOUNCE_MILLIS = 200L

internal class PackageSearchDataService(
    override val project: Project
) : RootDataModelProvider, SearchClient, TargetModuleSetter, SelectedPackageSetter, OperationExecutor, LifetimeProvider, Disposable, CoroutineScope {

    override val coroutineContext = SupervisorJob() + CoroutineName("PackageSearchDataService")

    override val lifetime = createLifetime()
    override val parentDisposable = lifetime.createNestedDisposable()

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private val dataChangeChannel = Channel<TraceInfo>(10)
    private val queryChangeChannel = Channel<SearchQueryChangeModel>(10)

    private val dataProvider = ProjectDataProvider(PackageSearchApiClient(ServerURLs.base))
    private val operationFactory = PackageSearchOperationFactory()
    private val operationExecutor = ModuleOperationExecutor()
    private val operationFailureRenderer = OperationFailureRenderer()

    private var knownRepositoriesRemoteInfo = listOf<ApiRepository>()
    private val searchQueryProperty = Property("")
    private val searchResultsProperty = Property<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?>(null)

    private val _targetModulesProperty = Property<TargetModules>(TargetModules.None)
    private val _selectedPackageModelProperty = Property<SelectedPackageModel<*>?>(null)
    private val _statusProperty = Property(DataStatus())
    private val _rootDataModelProperty = Property(RootDataModel.EMPTY)
    private val _filterOptionsProperty = Property(
        FilterOptions(
            onlyStable = configuration.onlyStable,
            onlyKotlinMultiplatform = configuration.onlyKotlinMultiplatform
        )
    )

    override val statusProperty: IPropertyView<DataStatus> = _statusProperty

    override val dataModelProperty: IPropertyView<RootDataModel> = _rootDataModelProperty

    init {
        logDebug("PKGSDataService#Starting up the Package Search root model...")

        launch {
            refreshKnownRepositories(TraceInfo(TraceInfo.TraceSource.INIT)) // Only doing it at startup, for now, as it won't change often
        }

        searchQueryProperty.advise(lifetime) { query ->
            val traceInfo = TraceInfo(TraceInfo.TraceSource.SEARCH_QUERY)
            logDebug(traceInfo, "PKGSDataService#searchQuery.advise()") { "searchQuery changed ('$query'), performing search..." }

            onSearchQueryChanged(query, traceInfo)
        }

        searchResultsProperty.advise(lifetime) {
            val traceInfo = TraceInfo(TraceInfo.TraceSource.SEARCH_RESULTS)
            logDebug(traceInfo, "PKGSDataService#searchResults.advise()") {
                val resultsInfo = if (it == null) {
                    "null"
                } else {
                    "${it.packages.size} packages, ${it.repositories.size} repositories"
                }
                "searchResults changed ($resultsInfo), refreshing data..."
            }
            refreshData(traceInfo)
        }
        _targetModulesProperty.advise(lifetime) {
            val traceInfo = TraceInfo(TraceInfo.TraceSource.TARGET_MODULES)
            logDebug(traceInfo, "PKGSDataService#searchResults.advise()") {
                "_targetModules changed (${it.javaClass.simpleName}, ${it.size} modules), refreshing data..."
            }
            refreshData(traceInfo)
        }
        _filterOptionsProperty.advise(lifetime) {
            val traceInfo = TraceInfo(TraceInfo.TraceSource.FILTERS)
            configuration.onlyStable = it.onlyStable
            configuration.onlyKotlinMultiplatform = it.onlyKotlinMultiplatform

            logDebug(traceInfo, "PKGSDataService#_filterOptions.advise()") { "_filters changed ($it), refreshing data..." }
            refreshData(traceInfo)
        }

        logDebug("PKGSDataService#PKGS root model initialized, registering auto-refresh...")

        ModuleChangesSignalProvider.obtainModuleChangesSignalFor(project, lifetime)
            .advise(lifetime) {
                val traceInfo = TraceInfo(TraceInfo.TraceSource.PROJECT_CHANGES)
                logDebug(traceInfo, "PKGSDataService#ModuleChangesSignalProvider.obtainModuleChangesSignalFor()") {
                    "Project module changes detected, refreshing data..."
                }
                refreshData(traceInfo)
            }

        queryChangeChannel.consumeAsFlow()
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .onEach {
                try {
                    performSearch(it.query, it.traceInfo)
                } catch (e: Throwable) {
                    logError(it.traceInfo, "onSearchQueryChangedFlow") { "Execution failed: ${e.message}" }
                } finally {
                    setStatus(isSearching = false)
                }
            }
            .launchIn(this)

        dataChangeChannel.consumeAsFlow()
            .debounce(DATA_DEBOUNCE_MILLIS)
            .onEach {
                try {
                    onDataChanged(it)
                } catch (e: Throwable) {
                    logError(it, "onDataChangedFlow") { "Execution failed: ${e.message}" }
                } finally {
                    setStatus(isRefreshingData = false)
                }
            }
            .launchIn(this)
    }

    private suspend fun refreshKnownRepositories(traceInfo: TraceInfo) = coroutineScope {
        setStatus(isRefreshingData = true)
        logDebug(traceInfo, "PKGSDataService#refreshKnownRepositories()") { "Refreshing known repositories from API..." }
        withContext(Dispatchers.IO) {
            try {
                withTimeout(API_TIMEOUT_MILLIS) {
                    yield()
                    dataProvider.fetchKnownRepositories()
                }
            } catch (e: CancellationException) {
                logDebug(traceInfo, "PKGSDataService#refreshKnownRepositories()", e) { "Refreshing known repositories cancelled" }
                setStatus(isSearching = false)
                ApiResult.Failure(e)
            }
        }
            .also { yield() }
            .onFailure {
                logError(traceInfo, "refreshKnownRepositories()") { "Failed to refresh known repositories list. $it" }
            }
            .onSuccess {
                knownRepositoriesRemoteInfo = it
                logInfo(traceInfo, "refreshKnownRepositories()") {
                    "Known repositories refreshed. We know of ${it.size} repo(s). Refreshing data..."
                }
                refreshData(traceInfo)
            }

        setStatus(isRefreshingData = true)
    }

    private data class SearchQueryChangeModel(val query: String, val traceInfo: TraceInfo)

    private fun onSearchQueryChanged(query: String, traceInfo: TraceInfo) {
        queryChangeChannel.offer(SearchQueryChangeModel(query, traceInfo))
    }

    private fun performSearch(query: String, traceInfo: TraceInfo) = launch {
        logDebug(traceInfo, "PKGSDataService#performSearch()") { "Searching for '$query'..." }
        if (query.isBlank()) {
            logDebug(traceInfo, "PKGSDataService#performSearch()") { "Query is empty, reverting to no results" }
            searchResultsProperty.set(null)
            return@launch
        }

        setStatus(isSearching = true)

        withTimeout(API_TIMEOUT_MILLIS) { dataProvider.doSearch(query, _filterOptionsProperty.value) }
            .onFailure {
                PackageSearchEventsLogger.onSearchFailed(project, query)
                logError(traceInfo, "performSearch()") { "Search failed for query '$query': ${it.message}" }
                showErrorNotification(
                    it.message,
                    PackageSearchBundle.message("packagesearch.search.client.searching.failed")
                )
            }
            .onSuccess {
                PackageSearchEventsLogger.onSearchResponse(project, query, items = it.packages)
                logDebug(traceInfo, "PKGSDataService#performSearch()") {
                    "Searching for '$query' completed, yielded ${it.packages.size} results in ${it.repositories.size} repositories"
                }
                searchResultsProperty.setWithAppUiDispatcher(it)
            }

        setStatus(isSearching = false)
    }

    private suspend fun <T> Property<T>.setWithAppUiDispatcher(value: T) = withContext(Dispatchers.AppUI) {
        set(value)
    }

    private fun refreshData(traceInfo: TraceInfo) {
        dataChangeChannel.offer(traceInfo)
    }

    private suspend fun onDataChanged(traceInfo: TraceInfo) {
        val currentStatus = _statusProperty.value
        if (currentStatus.isExecutingOperations) {
            logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Ignoring data changes (status: [$currentStatus])" }
            return
        }

        setStatus(isRefreshingData = true)
        logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Refreshing data..." }

        val targetModules = _targetModulesProperty.value
        val filterOptions = _filterOptionsProperty.value
        val currentSearchResults = searchResultsProperty.value
        val query = searchQueryProperty.value
        val selectedPackage = _selectedPackageModelProperty.value

        val moduleModels = fetchProjectModuleModels(targetModules, traceInfo)
        val targetProjectModules = targetModules.map { it.projectModule }

        val installedPackages = installedPackages(targetProjectModules, traceInfo)
            .filter { it.matches(query, filterOptions.onlyKotlinMultiplatform) }

        val installablePackages = installablePackages(currentSearchResults, installedPackages, traceInfo)
        val allKnownRepositories = allKnownRepositoryModels(moduleModels, knownRepositoriesRemoteInfo)
        val knownRepositoriesInTargetModules = allKnownRepositories.filterOnlyThoseUsedIn(targetModules)
        val packagesToUpdate = computePackageUpdates(installedPackages, filterOptions.onlyStable)

        logDebug(traceInfo, "PKGSDataService#onDataChanged()") {
            "New data: ${installedPackages.size} installed, ${installablePackages.size} installable, " +
                "${knownRepositoriesInTargetModules.size} known repos in target modules, ${moduleModels.size} modules"
        }

        val packageModels = installedPackages + installablePackages
        val headerData = computeHeaderData(
            installed = installedPackages,
            installable = installablePackages,
            isSearching = query.isNotEmpty(),
            onlyStable = filterOptions.onlyStable,
            targetModules = targetModules,
            knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
            allKnownRepositories = allKnownRepositories
        )

        val newData = RootDataModel(
            projectModules = moduleModels,
            packageModels = packageModels,
            packagesToUpdate = packagesToUpdate,
            knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
            allKnownRepositories = allKnownRepositories,
            headerData = headerData,
            targetModules = targetModules,
            selectedPackage = selectedPackage,
            filterOptions = filterOptions,
            traceInfo = traceInfo
        )

        if (newData != _rootDataModelProperty.value) {
            logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Sending data changes through" }
            _rootDataModelProperty.setWithAppUiDispatcher(newData)
            setStatus(isRefreshingData = false)

            rerunHighlightingOnOpenBuildFiles()
        } else {
            logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "No data changes detected, ignoring update" }
        }
    }

    private fun fetchProjectModuleModels(targetModules: TargetModules, traceInfo: TraceInfo): List<ModuleModel> {
        // Refresh project modules, this will cascade into updating the rest of the data
        val projectModules = ProjectModuleProvider.obtainAllProjectModulesFor(project).toList()

        if (targetModules is TargetModules.One && projectModules.none { it == targetModules.module.projectModule }) {
            logDebug(traceInfo, "PKGSDataService#fetchProjectModuleModels()") { "Target module doesn't exist anymore, resetting to 'All'" }
            setTargetModules(TargetModules.all(projectModules.toModuleModelsList()))
        }
        return projectModules.toModuleModelsList()
    }

    private fun List<ProjectModule>.toModuleModelsList() = map { moduleModelFrom(it) }

    private fun moduleModelFrom(projectModule: ProjectModule): ModuleModel {
        val repositories = projectModule.declaredRepositories()
            .map { repo -> RepositoryDeclaration(repo.id, repo.name, repo.url, projectModule) }
        return ModuleModel(projectModule, repositories)
    }

    private suspend fun installedPackages(projectModules: List<ProjectModule>, traceInfo: TraceInfo): List<PackageModel.Installed> {
        val dependenciesByModule = fetchProjectDependencies(projectModules, traceInfo)
        val usageInfoByDependency = mutableMapOf<UnifiedDependency, MutableList<DependencyUsageInfo>>()

        for (module in projectModules) {
            for (dependency in dependenciesByModule[module] ?: emptyList()) {
                // Skip packages we don't know the version for
                val rawVersion = dependency.coordinates.version

                val usageInfo = DependencyUsageInfo(
                    projectModule = module,
                    version = PackageVersion.from(rawVersion),
                    scope = PackageScope.from(dependency.scope),
                    availableScopes = module.moduleType.scopes(project)
                        .map { rawScope -> PackageScope.from(rawScope) }
                )
                val usageInfoList = usageInfoByDependency.getOrPut(dependency) { mutableListOf() }
                usageInfoList.add(usageInfo)
            }
        }

        val installedDependencies = dependenciesByModule.values.flatten()
            .mapNotNull { InstalledDependency.from(it) }

        val dependencyRemoteInfoMap = withTimeout(API_TIMEOUT_MILLIS) {
            dataProvider.fetchInfoFor(installedDependencies, traceInfo)
        }

        return usageInfoByDependency.mapNotNull { (dependency, usageInfo) ->
            val installedDependency = InstalledDependency.from(dependency)
            val remoteInfo = if (installedDependency != null) {
                dependencyRemoteInfoMap[installedDependency]
            } else {
                null
            }

            PackageModel.fromInstalledDependency(
                unifiedDependency = dependency,
                usageInfo = usageInfo,
                remoteInfo = remoteInfo
            )
        }.sortedBy { it.sortKey }
    }

    private fun fetchProjectDependencies(modules: List<ProjectModule>, traceInfo: TraceInfo): Map<ProjectModule, List<UnifiedDependency>> =
        modules.associateWith { module -> module.installedDependencies(traceInfo) }

    private fun ProjectModule.installedDependencies(traceInfo: TraceInfo): List<UnifiedDependency> = runReadAction {
        logDebug(traceInfo, "PKGSDataService#installedDependencies()") { "Fetching installed dependencies for module $name..." }

        ProjectModuleOperationProvider.forProjectModuleType(moduleType)
            ?.listDependenciesInModule(this)
            ?.toList()
            ?: emptyList()
    }

    private fun PackageModel.matches(query: String, onlyKotlinMultiplatform: Boolean): Boolean {
        if (onlyKotlinMultiplatform && !isKotlinMultiplatform) {
            return false
        }

        if (query.isBlank()) return true

        val queryTokens = query.split("\\b".toRegex())
            .filter { it.isNotBlank() }
            .map { it.toLowerCase(Locale.ROOT) }

        return queryTokens.any { searchableInfo.contains(it) }
    }

    private fun installablePackages(
        searchResults: ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?,
        installedPackages: List<PackageModel>,
        traceInfo: TraceInfo
    ): List<PackageModel.SearchResult> {
        logDebug(traceInfo, "PKGSDataService#installedDependencies()") { "Calculating installable dependencies from search results..." }
        if (searchResults == null || searchResults.packages.isEmpty()) return emptyList()

        val installedDependencies = installedPackages.map { InstalledDependency(it.groupId, it.artifactId) }

        return searchResults.packages
            .filterNot { installedDependencies.any { installed -> installed.matchesCoordinates(it) } }
            .mapNotNull { PackageModel.fromSearchResult(it) }
            .toList()
    }

    private fun allKnownRepositoryModels(
        allModules: List<ModuleModel>,
        knownRepositoriesRemoteInfo: List<ApiRepository>
    ) = KnownRepositories.All(
        knownRepositoriesRemoteInfo.map { remoteInfo ->
            val url = remoteInfo.url
            val id = remoteInfo.id

            RepositoryModel(
                id = id,
                name = remoteInfo.friendlyName,
                url = url,
                usageInfo = allModules.filter { module -> module.declaredRepositories.anyMatches(remoteInfo) }
                    .map { module -> RepositoryUsageInfo(module.projectModule) },
                remoteInfo = remoteInfo
            )
        }
    )

    private fun List<RepositoryDeclaration>.anyMatches(remoteInfo: ApiRepository): Boolean {
        val urls = (remoteInfo.alternateUrls ?: emptyList()) + remoteInfo.url
        val id = remoteInfo.id
        if (urls.isEmpty() && id.isBlank()) return false
        return any { declaredRepo ->
            declaredRepo.id == id || urls.any { knownRepoUrl -> areEquivalentUrls(declaredRepo.url, knownRepoUrl) }
        }
    }

    private fun areEquivalentUrls(first: String?, second: String?): Boolean {
        if (first == null || second == null) return false
        val firstUri = URI(first.trim().trimEnd('/', '?', '#'))
        val secondUri = URI(second.trim().trimEnd('/', '?', '#'))
        return firstUri.normalize() == secondUri.normalize()
    }

    private fun computePackageUpdates(
        installedPackages: List<PackageModel.Installed>,
        onlyStable: Boolean
    ): PackagesToUpdate {
        val updatesByModule = mutableMapOf<Module, MutableSet<PackagesToUpdate.PackageUpdateInfo>>()
        for (installedPackage in installedPackages) {
            if (installedPackage.remoteInfo == null) continue
            val latestVersion = installedPackage.getLatestAvailableVersion(onlyStable) as? PackageVersion.Named
                ?: continue

            for (usageInfo in installedPackage.usageInfo) {
                val currentVersion = usageInfo.version

                if (currentVersion < latestVersion) {
                    updatesByModule.getOrCreate(usageInfo.projectModule.nativeModule) { mutableSetOf() } +=
                        PackagesToUpdate.PackageUpdateInfo(installedPackage, usageInfo, latestVersion)
                }
            }
        }

        return PackagesToUpdate(updatesByModule)
    }

    private fun ProjectModule.declaredRepositories(): List<UnifiedDependencyRepository> = runReadAction {
        val declaredRepositories = (ProjectModuleOperationProvider.forProjectModuleType(moduleType)
            ?.listRepositoriesInModule(this)
            ?.toList()
            ?: emptyList())

        // This is a (sad) workaround for IDEA-267229 — when that's sorted, we shouldn't need this anymore.
        if (moduleType == MavenProjectModuleType && declaredRepositories.none { it.id == "central" }) {
            declaredRepositories + MAVEN_CENTRAL_UNIFIED_REPOSITORY
        } else {
            declaredRepositories
        }

    }

    private fun computeHeaderData(
        installed: List<PackageModel.Installed>,
        installable: List<PackageModel.SearchResult>,
        isSearching: Boolean,
        onlyStable: Boolean,
        targetModules: TargetModules,
        knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        allKnownRepositories: KnownRepositories.All
    ): PackagesHeaderData {
        val count = installed.count() + installable.count()
        val selectedModules = _targetModulesProperty.value
        val moduleNames = if (selectedModules.size == 1) {
            selectedModules.first().projectModule.name
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules").toLowerCase()
        }

        val title = if (isSearching) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults")
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.installedPackages.addedIn", moduleNames)
        }

        val updatablePackages = installed.filter { it.canBeUpgraded(onlyStable) }

        val operations = updatablePackages.flatMap { packageModel ->
            val newVersion = packageModel.getLatestAvailableVersion(onlyStable)
                ?: return@flatMap emptyList<PackageSearchOperation<*>>()

            val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
                packageModel = packageModel,
                selectedVersion = newVersion,
                allKnownRepositories = allKnownRepositories
            )
            operationFactory.createChangePackageVersionOperations(
                packageModel = packageModel,
                newVersion = newVersion,
                targetModules = targetModules,
                repoToInstall = repoToInstall
            )
        }

        return PackagesHeaderData(title, count, updatablePackages.count(), operations)
    }

    private suspend fun setStatus(
        isSearching: Boolean? = null,
        isRefreshingData: Boolean? = null,
        isExecutingOperations: Boolean? = null
    ) {
        val traceInfo = TraceInfo(TraceInfo.TraceSource.STATUS_CHANGES)
        val currentStatus = _statusProperty.value
        val newStatus = currentStatus.copy(
            isSearching = isSearching ?: currentStatus.isSearching,
            isRefreshingData = isRefreshingData ?: currentStatus.isRefreshingData,
            isExecutingOperations = isExecutingOperations ?: currentStatus.isExecutingOperations
        )

        if (currentStatus == newStatus) {
            logDebug(traceInfo, "PKGSDataService#setStatusAsync()") { "Ignoring status change (not really changed)" }
            return
        }
        _statusProperty.setWithAppUiDispatcher(newStatus)
        logDebug(traceInfo, "PKGSDataService#setStatusAsync()") { "Status changed: $newStatus" }
    }

    private fun rerunHighlightingOnOpenBuildFiles() {
        val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        val psiManager = PsiManager.getInstance(project)

        runReadAction {
            FileEditorManager.getInstance(project).openFiles.asSequence()
                .filter { virtualFile ->
                    val file = PsiUtil.getPsiFile(project, virtualFile)
                    ProjectModuleOperationProvider.forProjectPsiFileOrNull(project, file)
                        ?.hasSupportFor(project, file)
                        ?: false
                }
                .mapNotNull { psiManager.findFile(it) }
                .forEach { daemonCodeAnalyzer.restart(it) }
        }
    }

    override fun setTargetModules(targetModules: TargetModules) {
        if (targetModules == _targetModulesProperty.value) {
            logDebug("PKGSDataService#setTargetModules()") { "Ignoring target module change as it is the same we already have" }
            return
        }

        logDebug("PKGSDataService#setTargetModules()") {
            "Setting target modules: ${targetModules.javaClass.simpleName} (count: ${targetModules.size})"
        }
        launch { _targetModulesProperty.setWithAppUiDispatcher(targetModules) }
    }

    override fun setSelectedPackage(selectedPackageModel: SelectedPackageModel<*>?) {
        if (selectedPackageModel == _selectedPackageModelProperty.value) {
            logDebug("PKGSDataService#setSelectedPackage()") { "Ignoring selected package change as it is the same we already have" }
            return
        }

        logDebug("PKGSDataService#setSelectedPackage()") {
            "Setting selected package: ${selectedPackageModel?.packageModel?.identifier}"
        }
        launch { _selectedPackageModelProperty.setWithAppUiDispatcher(selectedPackageModel) }
    }

    override fun executeOperations(operations: List<PackageSearchOperation<*>>) {
        val traceInfo = TraceInfo(TraceInfo.TraceSource.EXECUTE_OPS)
        if (operations.isEmpty()) {
            logTrace(traceInfo, "PKGSDataService#execute()") { "Empty operations list, nothing to do" }
            return
        }

        launch {
            logDebug(traceInfo, "PKGSDataService#execute()") { "Executing ${operations.size} operation(s)..." }

            setStatus(isExecutingOperations = true)
            val failures = operations.mapNotNull { operation ->
                logTrace(traceInfo, "PKGSDataService#execute()") { "Executing $operation..." }
                withContext(Dispatchers.AppUI) { operationExecutor.doOperation(operation) }
            }

            setStatus(isExecutingOperations = false)

            logDebug(traceInfo, "PKGSDataService#execute()") { "Executed ${operations.size} operations, ${failures.size} failed" }

            if (failures.size == operations.size) {
                showErrorNotification(
                    PackageSearchBundle.message("packagesearch.operation.error.subtitle.allFailed"),
                    operationFailureRenderer.renderFailuresAsHtmlBulletList(failures)
                )
            } else if (failures.isNotEmpty()) {
                showErrorNotification(
                    PackageSearchBundle.message("packagesearch.operation.error.subtitle.someFailed"),
                    operationFailureRenderer.renderFailuresAsHtmlBulletList(failures)
                )
            }

            refreshData(traceInfo)
        }
    }

    private fun showErrorNotification(
        @Nls subtitle: String? = null,
        @Nls message: String
    ) {
        @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name
        NotificationGroupManagerImpl().getNotificationGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
            .createNotification(
                PackageSearchBundle.message("packagesearch.title"),
                message,
                NotificationType.ERROR
            )
            .setSubtitle(subtitle)
            .notify(project)
    }

    override fun setSearchQuery(query: String) {
        val normalizedQuery = StringUtils.normalizeSpace(query).trim()
        if (normalizedQuery == searchQueryProperty.value) {
            logDebug("PKGSDataService#setSearchQuery()") { "Ignoring search query update as it has not changed" }
            return
        }

        logDebug("PKGSDataService#setSearchQuery()") { "Search query changed: '$normalizedQuery'" }
        PackageSearchEventsLogger.onSearchRequest(project, normalizedQuery)
        launch { searchQueryProperty.setWithAppUiDispatcher(normalizedQuery) }
    }

    override fun setOnlyStable(onlyStable: Boolean) {
        if (onlyStable == _filterOptionsProperty.value.onlyStable) {
            logDebug("PKGSDataService#setOnlyStable()") { "Ignoring onlyStable change as it is the same we already have" }
            return
        }

        logDebug("PKGSDataService#setOnlyStable()") { "Setting onlyStable: $onlyStable" }
        launch { _filterOptionsProperty.setWithAppUiDispatcher(_filterOptionsProperty.value.copy(onlyStable = onlyStable)) }
    }

    override fun setOnlyKotlinMultiplatform(onlyKotlinMultiplatform: Boolean) {
        if (onlyKotlinMultiplatform == _filterOptionsProperty.value.onlyKotlinMultiplatform) {
            logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") {
                "Ignoring onlyKotlinMultiplatform change as it is the same we already have"
            }
            return
        }

        logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") { "Setting onlyKotlinMultiplatform: $onlyKotlinMultiplatform" }
        launch { _filterOptionsProperty.setWithAppUiDispatcher(_filterOptionsProperty.value.copy(onlyKotlinMultiplatform = onlyKotlinMultiplatform)) }
    }

    override fun dispose() {
        logDebug("PKGSDataService#dispose()") { "Disposing PackageSearchDataService..." }
        coroutineContext.cancel(CancellationException("Disposing service"))
    }
}
