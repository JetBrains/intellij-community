@file:Suppress("DEPRECATION")

package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.BackgroundLoadingBarController
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.batchAtIntervals
import com.jetbrains.packagesearch.intellij.plugin.util.catchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.coroutineModuleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.mapLatestTimedWithLoading
import com.jetbrains.packagesearch.intellij.plugin.util.modifiedBy
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesChangesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import com.jetbrains.packagesearch.intellij.plugin.util.parallelUpdatedKeys
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignals
import com.jetbrains.packagesearch.intellij.plugin.util.showBackgroundLoadingBar
import com.jetbrains.packagesearch.intellij.plugin.util.throttle
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.toolWindowManagerFlow
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import com.jetbrains.packagesearch.intellij.plugin.util.whileLoading
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.seconds

@Service(Service.Level.PROJECT)
internal class PackageSearchProjectService(private val project: Project) {

    private val retryFromErrorChannel = Channel<Unit>()
    private val restartChannel = Channel<Unit>()
    val dataProvider = ProjectDataProvider(
        PackageSearchApiClient(ServerURLs.base),
        project.packageSearchProjectCachesService.installedDependencyCache
    )

    private val projectModulesLoadingFlow = MutableStateFlow(false)
    private val knownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val moduleModelsLoadingFlow = MutableStateFlow(false)
    private val allInstalledKnownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val installedPackagesStep1LoadingFlow = MutableStateFlow(false)
    private val installedPackagesStep2LoadingFlow = MutableStateFlow(false)
    private val installedPackagesDifferenceLoadingFlow = MutableStateFlow(false)
    private val packageUpgradesLoadingFlow = MutableStateFlow(false)
    private val availableUpgradesLoadingFlow = MutableStateFlow(false)

    val canShowLoadingBar = MutableStateFlow(false)

    private val operationExecutedChannel = Channel<List<ProjectModule>>()

    private val json = Json { prettyPrint = true }

    private val cacheDirectory = project.packageSearchProjectCachesService.projectCacheDirectory.resolve("installedDependencies")

    val isLoadingFlow = combineTransform(
        projectModulesLoadingFlow,
        knownRepositoriesLoadingFlow,
        moduleModelsLoadingFlow,
        allInstalledKnownRepositoriesLoadingFlow,
        installedPackagesStep1LoadingFlow,
        installedPackagesStep2LoadingFlow,
        installedPackagesDifferenceLoadingFlow,
        packageUpgradesLoadingFlow
    ) { booleans -> emit(booleans.any { it }) }
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, false)

    private val projectModulesSharedFlow = project.trustedProjectFlow.flatMapLatest { isProjectTrusted ->
        if (isProjectTrusted) project.nativeModulesChangesFlow else flowOf(emptyList())
    }
        .replayOnSignals(
            retryFromErrorChannel.receiveAsFlow().throttle(10.seconds),
            project.moduleChangesSignalFlow,
            restartChannel.receiveAsFlow()
        )
        .mapLatestTimedWithLoading("projectModulesSharedFlow", projectModulesLoadingFlow) { modules ->
            val moduleTransformations = project.moduleTransformers.map { transformer ->
                async { readAction { runCatching { transformer.transformModules(project, modules) } }.getOrThrow() }
            }

            val coroutinesModulesTransformations = project.coroutineModuleTransformers
                .map { async { it.transformModules(project, modules) } }
                .awaitAll()
                .flatten()

            moduleTransformations.awaitAll().flatten() + coroutinesModulesTransformations
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#projectModulesSharedFlow",
            message = "Error while elaborating latest project modules",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    val projectModulesStateFlow = projectModulesSharedFlow.stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val isAvailable
        get() = projectModulesStateFlow.value.isNotEmpty()

    private val knownRepositoriesFlow = timer(Duration.hours(1))
        .mapLatestTimedWithLoading("knownRepositoriesFlow", knownRepositoriesLoadingFlow) { dataProvider.fetchKnownRepositories() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#knownRepositoriesFlow",
            message = "Error while refreshing known repositories",
            fallbackValue = emptyList()
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    private val buildFileChangesFlow = combine(
        projectModulesSharedFlow,
        project.filesChangedEventFlow.map { it.mapNotNull { it.file } }
    ) { modules, changedBuildFiles -> modules.filter { it.buildFile in changedBuildFiles } }
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    private val projectModulesChangesFlow = merge(
        buildFileChangesFlow.filter { it.isNotEmpty() },
        operationExecutedChannel.consumeAsFlow()
    )
        .batchAtIntervals(1.seconds)
        .map { it.flatMap { it }.distinct() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#projectModulesChangesFlow",
            message = "Error while checking Modules changes",
            fallbackValue = emptyList()
        )
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    val moduleModelsStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading(
            loggingContext = "moduleModelsStateFlow",
            loadingFlow = moduleModelsLoadingFlow
        ) { projectModules ->
            projectModules.parallelMap { it to ModuleModel(it) }.toMap()
        }
        .modifiedBy(projectModulesChangesFlow) { repositories, changedModules ->
            repositories.parallelUpdatedKeys(changedModules) { ModuleModel(it) }
        }
        .map { it.values.toList() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#moduleModelsStateFlow",
            message = "Error while evaluating modules models",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val allInstalledKnownRepositoriesFlow =
        combine(moduleModelsStateFlow, knownRepositoriesFlow) { moduleModels, repos -> moduleModels to repos }
            .mapLatestTimedWithLoading(
                loggingContext = "allInstalledKnownRepositoriesFlow",
                loadingFlow = allInstalledKnownRepositoriesLoadingFlow
            ) { (moduleModels, repos) ->
                allKnownRepositoryModels(moduleModels, repos)
            }
            .catchAndLog(
                context = "${this::class.qualifiedName}#allInstalledKnownRepositoriesFlow",
                message = "Error while evaluating installed repositories",
                fallbackValue = KnownRepositories.All.EMPTY
            )
            .stateIn(project.lifecycleScope, SharingStarted.Eagerly, KnownRepositories.All.EMPTY)

    val dependenciesByModuleStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading("installedPackagesStep1LoadingFlow", installedPackagesStep1LoadingFlow) {
            fetchProjectDependencies(it, cacheDirectory, json)
        }
        .modifiedBy(projectModulesChangesFlow) { installed, changedModules ->
            val (result, time) = installedPackagesDifferenceLoadingFlow.whileLoading {
                installed.parallelUpdatedKeys(changedModules) { it.installedDependencies(cacheDirectory, json) }
            }
            logTrace("installedPackagesStep1LoadingFlow") {
                "Took ${time} to process diffs for ${changedModules.size} module" + if (changedModules.size > 1) "s" else ""
            }
            result
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#dependenciesByModuleStateFlow",
            message = "Error while evaluating installed dependencies",
            fallbackValue = emptyMap(),
            retryChannel = retryFromErrorChannel
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyMap())

    val installedPackagesStateFlow = dependenciesByModuleStateFlow
        .mapLatestTimedWithLoading("installedPackagesStep2LoadingFlow", installedPackagesStep2LoadingFlow) {
            installedPackages(
                it,
                project,
                dataProvider,
                TraceInfo(TraceInfo.TraceSource.INIT)
            )
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#installedPackagesStateFlow",
            message = "Error while evaluating installed packages",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val packageUpgradesStateFlow = combine(
        installedPackagesStateFlow,
        moduleModelsStateFlow,
        knownRepositoriesFlow
    ) { installedPackages, moduleModels, repos ->
        coroutineScope {
            availableUpgradesLoadingFlow.whileLoading {
                val allKnownRepos = allKnownRepositoryModels(moduleModels, repos)
                val nativeModulesMap = moduleModels.associateBy { it.projectModule }

                val getUpgrades: suspend (Boolean) -> PackagesToUpgrade = {
                    computePackageUpgrades(installedPackages, it, packageVersionNormalizer, allKnownRepos, nativeModulesMap)
                }

                val stableUpdates = async { getUpgrades(true) }
                val allUpdates = async { getUpgrades(false) }
                PackageUpgradeCandidates(stableUpdates.await(), allUpdates.await())
            }.value
        }
    }
        .catchAndLog(
            context = "${this::class.qualifiedName}#packageUpgradesStateFlow",
            message = "Error while evaluating packages upgrade candidates",
            fallbackValue = PackageUpgradeCandidates.EMPTY,
            retryChannel = retryFromErrorChannel
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, PackageUpgradeCandidates.EMPTY)

    init {
        // allows rerunning PKGS inspections on already opened files
        // when the data is finally available or changes for PackageUpdateInspection
        // or when a build file changes
        packageUpgradesStateFlow.onEach { DaemonCodeAnalyzer.getInstance(project).restart() }
            .launchIn(project.lifecycleScope)

        var controller: BackgroundLoadingBarController? = null

        project.toolWindowManagerFlow
            .filter { it.id == PackageSearchToolWindowFactory.ToolWindowId }
            .take(1)
            .onEach { canShowLoadingBar.emit(true) }
            .launchIn(project.lifecycleScope)

        if (PluginEnvironment.isNonModalLoadingEnabled) {
            canShowLoadingBar.filter { it }
                .flatMapLatest { isLoadingFlow }
                .throttle(1.seconds)
                .onEach { controller?.clear() }
                .filter { it }
                .onEach {
                    controller = showBackgroundLoadingBar(
                        project,
                        PackageSearchBundle.message("toolwindow.stripe.Dependencies"),
                        PackageSearchBundle.message("packagesearch.ui.loading")
                    )
                }.launchIn(project.lifecycleScope)
        }

    }

    fun notifyOperationExecuted(successes: List<ProjectModule>) {
        operationExecutedChannel.trySend(successes)
    }

    suspend fun restart() {
        restartChannel.send(Unit)
    }
}
