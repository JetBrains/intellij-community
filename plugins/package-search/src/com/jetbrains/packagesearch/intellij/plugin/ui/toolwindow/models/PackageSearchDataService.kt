package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiRepository
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.OperationFailureRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.combine
import com.jetbrains.packagesearch.intellij.plugin.util.launchLoop
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchModulesChangesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import kotlin.time.toDuration

internal class PackageSearchDataService(
    override val project: Project
) : RootDataModelProvider, SearchClient, TargetModuleSetter, SelectedPackageSetter, OperationExecutor, CoroutineScope by project.lifecycleScope {

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private val dataChangeChannel = Channel<Unit>()

    private val dataProvider = ProjectDataProvider(PackageSearchApiClient(ServerURLs.base))
    private val operationFactory = PackageSearchOperationFactory()
    private val operationExecutor = ModuleOperationExecutor()
    private val operationFailureRenderer = OperationFailureRenderer()

    private var knownRepositoriesRemoteInfo = MutableStateFlow<List<ApiRepository>>(emptyList())
    private val searchQueryState = MutableStateFlow("")
    private val searchResultsState: MutableStateFlow<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?> =
        MutableStateFlow(null)

    private val targetModulesState = MutableStateFlow<TargetModules>(TargetModules.None)
    private val selectedPackageModelState = MutableStateFlow<SelectedPackageModel<*>?>(null)
    private val filterOptionsState = MutableStateFlow(
        FilterOptions(
            onlyStable = configuration.onlyStable,
            onlyKotlinMultiplatform = configuration.onlyKotlinMultiplatform
        )
    )

    override val dataStatusState: MutableStateFlow<DataStatus> = MutableStateFlow(DataStatus())

    override val dataModelFlow: StateFlow<RootDataModel> = combine(
        searchQueryState,
        searchResultsState,
        targetModulesState,
        filterOptionsState,
        project.packageSearchModulesChangesFlow,
        knownRepositoriesRemoteInfo,
        selectedPackageModelState
    ) { searchQuery, searchResult, targetModules,
        filterOptions, modules, apiRepositories, selectedPackage ->
        OnDataChangedIntermediateModel(
            traceInfo = TraceInfo(TraceInfo.TraceSource.DATA_CHANGED),
            searchQuery = searchQuery,
            searchResult = searchResult,
            targetModules = targetModules.refreshWith(modules),
            filterOptions = filterOptions,
            projectModules = modules,
            knownRepositories = apiRepositories,
            selectedPackageModel = selectedPackage
        )
    }.replayOnSignal(dataChangeChannel.consumeAsFlow())
        .debounce(200.toDuration(MILLISECONDS))
        .map { it.toRootDataModel() }
        .onEach { rerunHighlightingOnOpenBuildFiles() }
        .catch {
            logError(contextName = "dataModelFlow", throwable = it) { "Error while processing latest model change." }
        }
        .stateIn(this, SharingStarted.Lazily, RootDataModel.EMPTY)

    private data class OnDataChangedIntermediateModel(
        val traceInfo: TraceInfo,
        val searchQuery: String,
        val searchResult: ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?,
        val targetModules: TargetModules,
        val filterOptions: FilterOptions,
        val projectModules: List<ProjectModule>,
        val knownRepositories: List<ApiRepository>,
        val selectedPackageModel: SelectedPackageModel<*>?
    )

    init {
        logDebug("PKGSDataService#Starting up the Package Search root model...")

        checkNotificationsSetupIsCorrect()

        launchLoop(1.toDuration(HOURS)) {
            refreshKnownRepositories(TraceInfo(TraceInfo.TraceSource.INIT))
        }

        searchQueryState.onEach { performSearch(it, TraceInfo(TraceInfo.TraceSource.SEARCH_QUERY)) }
            .catch { error ->
                when (error) {
                    is TimeoutCancellationException, is TimeoutException, is SocketTimeoutException ->
                        showErrorNotification(message = PackageSearchBundle.message("packagesearch.search.client.searching.failed.timeout"))
                    else -> logError("searchQueryState", error) { "Error while retrieving search results." }
                }
            }
            .launchIn(this)
    }

    private fun checkNotificationsSetupIsCorrect() {
        checkNotNull(NotificationGroupManager.getInstance().getNotificationGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)) {
            "Notification group $PACKAGE_SEARCH_NOTIFICATION_GROUP_ID is not registered"
        }
    }

    private suspend fun refreshKnownRepositories(traceInfo: TraceInfo) = coroutineScope {
        setStatus(isRefreshingData = true)
        logDebug(traceInfo, "PKGSDataService#refreshKnownRepositories()") { "Refreshing known repositories from API..." }
        dataProvider.fetchKnownRepositories()
            .onFailure { logError(traceInfo, "refreshKnownRepositories()", it) { "Failed to refresh known repositories list." } }
            .onSuccess {
                logInfo(traceInfo, "refreshKnownRepositories()") { "Known repositories refreshed. We know of ${it.size} repo(s). Refreshing data..." }
                knownRepositoriesRemoteInfo.emit(it)
            }
        setStatus(isRefreshingData = false)
    }

    private suspend fun performSearch(query: String, traceInfo: TraceInfo) {
        logDebug(traceInfo, "PKGSDataService#performSearch()") { "Searching for '$query'..." }
        if (query.isBlank()) {
            logDebug(traceInfo, "PKGSDataService#performSearch()") { "Query is empty, reverting to no results" }
            searchResultsState.emit(null)
            return
        }

        setStatus(isSearching = true)

        dataProvider.doSearch(query, filterOptionsState.value)
            .onFailure {
                logError(traceInfo, "performSearch()") { "Search failed for query '$query': ${it.message}" }
                showErrorNotification(
                    it.message,
                    PackageSearchBundle.message("packagesearch.search.client.searching.failed")
                )
            }
            .onSuccess {
                logDebug(traceInfo, "PKGSDataService#performSearch()") {
                    "Searching for '$query' completed, yielded ${it.packages.size} results in ${it.repositories.size} repositories"
                }
                searchResultsState.emit(it)
            }
        setStatus(isSearching = false)
    }

    private suspend fun OnDataChangedIntermediateModel.toRootDataModel(): RootDataModel {
        setStatus(isRefreshingData = true)
        logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Refreshing data..." }

        val moduleModels = fetchProjectModuleModels(targetModules, traceInfo, projectModules)
        val targetProjectModules = targetModules.map { it.projectModule }

        val installedPackages = installedPackages(targetProjectModules, traceInfo)
            .filter { it.matches(searchQuery, filterOptions.onlyKotlinMultiplatform) }

        val installablePackages = installablePackages(searchResult, installedPackages, traceInfo)
        val allKnownRepositories = allKnownRepositoryModels(moduleModels, knownRepositories)
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
            isSearching = searchQuery.isNotEmpty(),
            onlyStable = filterOptions.onlyStable,
            targetModules = targetModules,
            knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
            allKnownRepositories = allKnownRepositories
        )

        val newData = RootDataModel(
            moduleModels = moduleModels,
            packageModels = packageModels,
            packagesToUpdate = packagesToUpdate,
            knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
            allKnownRepositories = allKnownRepositories,
            headerData = headerData,
            targetModules = targetModules,
            selectedPackage = selectedPackageModel,
            filterOptions = filterOptions,
            traceInfo = traceInfo
        )

        logDebug(traceInfo, "PKGSDataService#onDataChanged()") { "Sending data changes through" }
        setStatus(isRefreshingData = false)
        return newData
    }

    private fun fetchProjectModuleModels(targetModules: TargetModules, traceInfo: TraceInfo, projectModules: List<ProjectModule>): List<ModuleModel> {
        // Refresh project modules, this will cascade into updating the rest of the data

        val moduleModels = runReadAction {
            projectModules.map { ModuleModel(it) }
        }
        if (targetModules is TargetModules.One && projectModules.none { it == targetModules.module.projectModule }) {
            logDebug(traceInfo, "PKGSDataService#fetchProjectModuleModels()") { "Target module doesn't exist anymore, resetting to 'All'" }
            setTargetModules(TargetModules.all(moduleModels))
        }
        return moduleModels
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

        val dependencyRemoteInfoMap = dataProvider.fetchInfoFor(installedDependencies, traceInfo)

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

    private suspend fun fetchProjectDependencies(modules: List<ProjectModule>, traceInfo: TraceInfo): Map<ProjectModule, List<UnifiedDependency>> =
        modules.associateWith { module -> module.installedDependencies(traceInfo) }

    private suspend fun ProjectModule.installedDependencies(traceInfo: TraceInfo): List<UnifiedDependency> = readAction { progress ->
        logDebug(traceInfo, "PKGSDataService#installedDependencies()") { "Fetching installed dependencies for module $name..." }
        ProjectModuleOperationProvider.forProjectModuleType(moduleType)
            ?.also { progress.checkCancelled() }
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
        val firstUri = tryParsingAsURI(first) ?: return false
        val secondUri = tryParsingAsURI(second) ?: return false
        return firstUri.normalize() == secondUri.normalize()
    }

    private fun tryParsingAsURI(rawValue: String): URI? =
        try {
            URI(rawValue.trim().trimEnd('/', '?', '#'))
        } catch (e: Exception) {
            logInfo("PackageSearchDataService#tryParsingAsURI") { "Unable to parse URI: '$rawValue'" }
            null
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

    private inline fun <K : Any, V : Any> MutableMap<K, V>.getOrCreate(key: K, crossinline creator: (K) -> V): V =
        this[key] ?: creator(key).let {
            this[key] = it
            return it
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
        val selectedModules = targetModulesState.value
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
        val currentStatus = dataStatusState.value
        val newStatus = currentStatus.copy(
            isSearching = isSearching ?: currentStatus.isSearching,
            isRefreshingData = isRefreshingData ?: currentStatus.isRefreshingData,
            isExecutingOperations = isExecutingOperations ?: currentStatus.isExecutingOperations
        )

        if (currentStatus == newStatus) {
            logDebug(traceInfo, "PKGSDataService#setStatusAsync()") { "Ignoring status change (not really changed)" }
            return
        }
        dataStatusState.emit(newStatus)
        logDebug(traceInfo, "PKGSDataService#setStatusAsync()") { "Status changed: $newStatus" }
    }

    private fun rerunHighlightingOnOpenBuildFiles() {
        val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        val psiManager = PsiManager.getInstance(project)

        runReadAction {
            FileEditorManager.getInstance(project).openFiles.asSequence()
                .filter { virtualFile ->
                    try {
                        val file = psiManager.findFile(virtualFile) ?: return@filter false
                        ProjectModuleOperationProvider.forProjectPsiFileOrNull(project, file)
                            ?.hasSupportFor(project, file)
                            ?: false
                    } catch (ignored: Throwable) {
                        logWarn(contextName = "PackageSearchDataService#rerunHighlightingOnOpenBuildFiles", ignored) {
                            "Error while filtering open files to trigger highlight rerun for"
                        }
                        false
                    }
                }
                .mapNotNull { psiManager.findFile(it) }
                .forEach { daemonCodeAnalyzer.restart(it) }
        }
    }

    override fun setTargetModules(targetModules: TargetModules) {
        logDebug("PKGSDataService#setTargetModules()") {
            "Setting target modules: ${targetModules.javaClass.simpleName} (count: ${targetModules.size})"
        }
        launch {
            targetModulesState.emit(targetModules)
        }
    }

    override suspend fun setSelectedPackage(selectedPackageModel: SelectedPackageModel<*>?) {
        logDebug("PKGSDataService#setSelectedPackage()") {
            "Setting selected package: ${selectedPackageModel?.packageModel?.identifier}"
        }
        selectedPackageModelState.emit(selectedPackageModel)
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

            dataChangeChannel.send(Unit)
        }
    }

    private fun showErrorNotification(
        @Nls subtitle: String? = null,
        @Nls message: String
    ) {
        @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name
        NotificationGroupManager.getInstance().getNotificationGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
            .createNotification(
                title = PackageSearchBundle.message("packagesearch.title"),
                content = message,
                type = NotificationType.ERROR
            )
            .setSubtitle(subtitle)
            .notify(project)
    }

    override fun setSearchQuery(query: String) {
        val normalizedQuery = StringUtils.normalizeSpace(query).trim()
        logDebug("PKGSDataService#setSearchQuery()") { "Search query changed: '$normalizedQuery'" }
        PackageSearchEventsLogger.logSearchRequest(normalizedQuery)
        launch { searchQueryState.emit(normalizedQuery) }
    }

    override fun setOnlyStable(onlyStable: Boolean) {
        if (onlyStable == filterOptionsState.value.onlyStable) {
            logDebug("PKGSDataService#setOnlyStable()") { "Ignoring onlyStable change as it is the same we already have" }
            return
        }

        logDebug("PKGSDataService#setOnlyStable()") { "Setting onlyStable: $onlyStable" }
        launch { filterOptionsState.emit(filterOptionsState.value.copy(onlyStable = onlyStable)) }
    }

    override fun setOnlyKotlinMultiplatform(onlyKotlinMultiplatform: Boolean) {
        if (onlyKotlinMultiplatform == filterOptionsState.value.onlyKotlinMultiplatform) {
            logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") {
                "Ignoring onlyKotlinMultiplatform change as it is the same we already have"
            }
            return
        }

        logDebug("PKGSDataService#setOnlyKotlinMultiplatform()") { "Setting onlyKotlinMultiplatform: $onlyKotlinMultiplatform" }
        launch { filterOptionsState.emit(filterOptionsState.value.copy(onlyKotlinMultiplatform = onlyKotlinMultiplatform)) }
    }
}
