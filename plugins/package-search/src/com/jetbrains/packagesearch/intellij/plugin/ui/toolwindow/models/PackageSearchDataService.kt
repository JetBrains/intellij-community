package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationGroupManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
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
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.OperationFailureRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.rd.util.getOrCreate
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls

private const val API_TIMEOUT_MILLIS = 10_000L
private const val DATA_DEBOUNCE_MILLIS = 100L
private const val SEARCH_DEBOUNCE_MILLIS = 200L

@Suppress("EXPERIMENTAL_API_USAGE") // Just playing around... can't use StateFlows yet
internal class PackageSearchDataService(
    override val project: Project
) : RootDataModelProvider, SearchClient, TargetModuleSetter, SelectedPackageSetter, OperationExecutor, LifetimeProvider, Disposable {

    private val mainScope = CoroutineScope(SupervisorJob() + AppUIExecutor.onUiThread().coroutineDispatchingContext()) +
        CoroutineName("PackageSearchDataService")

    override val lifetime = createLifetime()
    override val parentDisposable = lifetime.createNestedDisposable()

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private var dataChangeJob: Job? = null
    private var queryChangeJob: Job? = null

    private val dataProvider = ProjectDataProvider(PackageSearchApiClient(ServerURLs.base))
    private val operationFactory = PackageSearchOperationFactory()
    private val operationExecutor = ModuleOperationExecutor(project)
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

        mainScope.launch {
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
    }

    @RequiresEdt
    private suspend fun refreshKnownRepositories(traceInfo: TraceInfo) {
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

    @RequiresEdt
    private fun onSearchQueryChanged(query: String, traceInfo: TraceInfo) {
        queryChangeJob?.cancel()
        queryChangeJob = mainScope.launch {
            try {
                performSearch(query, traceInfo)
            } catch (e: CancellationException) {
                logTrace(traceInfo, "onSearchQueryChangedActor") { "Execution cancelled: ${e.message}" }
                setStatus(isSearching = false)
            }
        }
    }

    @RequiresEdt
    private suspend fun performSearch(query: String, traceInfo: TraceInfo) {
        logDebug(traceInfo, "PKGSDataService#performSearch()") { "Searching for '$query'..." }
        if (query.isBlank()) {
            logDebug(traceInfo, "PKGSDataService#performSearch()") { "Query is empty, reverting to no results" }
            searchResultsProperty.set(null)
            return
        }

        setStatus(isSearching = true)

        delay(SEARCH_DEBOUNCE_MILLIS) // Debounce

        withContext(Dispatchers.IO) {
            try {
                withTimeout(API_TIMEOUT_MILLIS) {
                    yield()
                    dataProvider.doSearch(query, _filterOptionsProperty.value)
                }
            } catch (e: CancellationException) {
                logDebug(traceInfo, "PKGSDataService#performSearch()", e) { "Searching for '$query' cancelled" }
                setStatus(isSearching = false)
                ApiResult.Failure(e)
            }
        }
            .also { yield() }
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
                searchResultsProperty.set(it)
            }

        setStatus(isSearching = false)
    }

    private fun refreshData(traceInfo: TraceInfo) {
        dataChangeJob?.cancel()
        dataChangeJob = mainScope.launch {
            try {
                delay(DATA_DEBOUNCE_MILLIS)
                onDataChanged(traceInfo)
            } catch (e: CancellationException) {
                logTrace(traceInfo, "onDataChangedActor") { "Execution cancelled: ${e.message}" }
                setStatus(isRefreshingData = false)
            }
        }
        onSearchQueryChanged(searchQueryProperty.value, traceInfo)
    }

    @RequiresEdt
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

        mainScope.launch(Dispatchers.IO) {
            val moduleModels = fetchProjectModuleModels(targetModules, traceInfo)
            val targetProjectModules = targetModules.map { it.projectModule }

            val installedPackages = installedPackages(targetProjectModules, traceInfo)
                .filter { it.matches(query, filterOptions.onlyKotlinMultiplatform) }

            val installablePackages = installablePackages(currentSearchResults, installedPackages, traceInfo)
            val allKnownRepositories = allKnownRepositoryModels(moduleModels, knownRepositoriesRemoteInfo)
            val knownRepositoriesInTargetModules = allKnownRepositories.filterOnlyThoseUsedIn(targetModules)
            val packagesToUpdate = computePackageUpdates(installedPackages, filterOptions.onlyStable)

            yield()

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

            yield()
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
                yield()
                logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Sending data changes through" }
                mainScope.launch {
                    _rootDataModelProperty.set(newData)
                    setStatus(isRefreshingData = false)
                }

                rerunHighlightingOnOpenBuildFiles()
            } else {
                logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "No data changes detected, ignoring update" }
            }
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

    @RequiresBackgroundThread
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

        try {
            val dependencyRemoteInfoMap = withContext(Dispatchers.IO) {
                withTimeout(API_TIMEOUT_MILLIS) {
                    yield()
                    dataProvider.fetchInfoFor(installedDependencies, traceInfo)
                }
            }
            yield()

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
        } catch (e: CancellationException) {
            logDebug(traceInfo, "PKGSDataService#installedPackages()", e) { "Fetching installed packages info cancelled" }
            setStatus(isSearching = false)
            throw e
        }
    }

    @RequiresBackgroundThread
    private fun fetchProjectDependencies(modules: List<ProjectModule>, traceInfo: TraceInfo): Map<ProjectModule, List<UnifiedDependency>> =
        modules.associateWith { module -> module.installedDependencies(traceInfo) }

    @RequiresBackgroundThread
    private fun ProjectModule.installedDependencies(traceInfo: TraceInfo): List<UnifiedDependency> {
        logDebug(traceInfo, "PKGSDataService#installedDependencies()") { "Fetching installed dependencies for module $name..." }

        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(moduleType)
            ?: return emptyList()
        return operationProvider.listDependenciesInProject(project, buildFile).toList()
    }

    @RequiresBackgroundThread
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

    @RequiresBackgroundThread
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

    @RequiresBackgroundThread
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
        val urls = ((remoteInfo.alternateUrls ?: emptyList()) + remoteInfo.url).filterNotNull()
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

    @RequiresBackgroundThread
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

    @RequiresBackgroundThread
    private fun ProjectModule.declaredRepositories(): List<UnifiedDependencyRepository> {
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(moduleType)
            ?: return emptyList()

        return operationProvider.listRepositoriesInProject(project, buildFile).toList()
    }

    @RequiresBackgroundThread
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

    private fun setStatus(
        isSearching: Boolean? = null,
        isRefreshingData: Boolean? = null,
        isExecutingOperations: Boolean? = null
    ) {
        mainScope.launch {
            val traceInfo = TraceInfo(TraceInfo.TraceSource.STATUS_CHANGES)
            val currentStatus = _statusProperty.value
            val newStatus = currentStatus.copy(
                isSearching = isSearching ?: currentStatus.isSearching,
                isRefreshingData = isRefreshingData ?: currentStatus.isRefreshingData,
                isExecutingOperations = isExecutingOperations ?: currentStatus.isExecutingOperations
            )

            if (currentStatus == newStatus) {
                logDebug(traceInfo, "PKGSDataService#setStatus()") { "Ignoring status change (not really changed)" }
                return@launch
            }

            _statusProperty.set(newStatus)
            logDebug(traceInfo, "PKGSDataService#setStatus()") { "Status changed: $newStatus" }
        }
    }

    @RequiresBackgroundThread
    private fun rerunHighlightingOnOpenBuildFiles() {
        val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        val psiManager = PsiManager.getInstance(project)

        runReadAction {
            FileEditorManager.getInstance(project).openFiles.asSequence()
                .filter { virtualFile ->
                    val file = PsiUtil.getPsiFile(project, virtualFile)
                    ProjectModuleOperationProvider.forProjectPsiFile(project, file)
                        ?.hasSupportFor(project, file)
                        ?: false
                }
                .mapNotNull { psiManager.findFile(it) }
                .forEach { daemonCodeAnalyzer.restart(it) }
        }
    }

    override fun setTargetModules(targetModules: TargetModules) = AppUIExecutor.onUiThread().execute {
        if (targetModules == _targetModulesProperty.value) {
            logDebug("PKGSDataService#setTargetModules()") { "Ignoring target module change as it is the same we already have" }
            return@execute
        }

        logDebug("PKGSDataService#setTargetModules()") {
            "Setting target modules: ${targetModules.javaClass.simpleName} (count: ${targetModules.size})"
        }
        _targetModulesProperty.set(targetModules)
    }

    override fun setSelectedPackage(selectedPackageModel: SelectedPackageModel<*>?) = AppUIExecutor.onUiThread().execute {
        if (selectedPackageModel == _selectedPackageModelProperty.value) {
            logDebug("PKGSDataService#setSelectedPackage()") { "Ignoring selected package change as it is the same we already have" }
            return@execute
        }

        logDebug("PKGSDataService#setSelectedPackage()") {
            "Setting selected package: ${selectedPackageModel?.packageModel?.identifier}"
        }
        _selectedPackageModelProperty.set(selectedPackageModel)
    }

    override fun executeOperations(operations: List<PackageSearchOperation<*>>) {
        val traceInfo = TraceInfo(TraceInfo.TraceSource.EXECUTE_OPS)
        if (operations.isEmpty()) {
            logTrace(traceInfo, "PKGSDataService#execute()") { "Empty operations list, nothing to do" }
            return
        }

        mainScope.launch {
            logDebug(traceInfo, "PKGSDataService#execute()") { "Executing ${operations.size} operation(s)..." }

            setStatus(isExecutingOperations = true)
            val failures = operations.map { operation ->
                logTrace(traceInfo, "PKGSDataService#execute()") { "Executing $operation..." }
                async(Dispatchers.Default) { operationExecutor.doOperation(operation) }
            }
                .awaitAll()
                .filterNotNull()

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

    @RequiresEdt
    private fun showErrorNotification(
        @Nls subtitle: String? = null,
        @Nls message: String
    ) {
        @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name
        NotificationGroupManagerImpl().getNotificationGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
            .createNotification(
                PackageSearchBundle.message("packagesearch.title"),
                subtitle,
                message,
                NotificationType.ERROR
            )
            .notify(project)
    }

    override fun setSearchQuery(query: String) = AppUIExecutor.onUiThread().execute {
        val normalizedQuery = StringUtils.normalizeSpace(query).trim()
        if (normalizedQuery == searchQueryProperty.value) {
            logDebug("PKGSDataService#setSearchQuery()") { "Ignoring search query update as it has not changed" }
            return@execute
        }

        logDebug("PKGSDataService#setSearchQuery()") { "Search query changed: '$normalizedQuery'" }
        PackageSearchEventsLogger.onSearchRequest(project, normalizedQuery)
        searchQueryProperty.set(normalizedQuery)
    }

    override fun setOnlyStable(onlyStable: Boolean) = AppUIExecutor.onUiThread().execute {
        if (onlyStable == _filterOptionsProperty.value.onlyStable) {
            logDebug("PKGSDataService#setOnlyStable()") { "Ignoring onlyStable change as it is the same we already have" }
            return@execute
        }

        logDebug("PKGSDataService#setOnlyStable()") { "Setting onlyStable: $onlyStable" }
        _filterOptionsProperty.set(_filterOptionsProperty.value.copy(onlyStable = onlyStable))
    }

    override fun setOnlyKotlinMultiplatform(onlyKotlinMultiplatform: Boolean) = AppUIExecutor.onUiThread().execute {
        if (onlyKotlinMultiplatform == _filterOptionsProperty.value.onlyKotlinMultiplatform) {
            logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") {
                "Ignoring onlyKotlinMultiplatform change as it is the same we already have"
            }
            return@execute
        }

        logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") { "Setting onlyKotlinMultiplatform: $onlyKotlinMultiplatform" }
        _filterOptionsProperty.set(_filterOptionsProperty.value.copy(onlyKotlinMultiplatform = onlyKotlinMultiplatform))
    }

    override fun dispose() {
        logDebug("PKGSDataService#dispose()") { "Disposing PackageSearchDataService..." }
        dataChangeJob?.cancel("Disposing service")
        queryChangeJob?.cancel("Disposing service")
        mainScope.cancel("Disposing service")
    }
}
