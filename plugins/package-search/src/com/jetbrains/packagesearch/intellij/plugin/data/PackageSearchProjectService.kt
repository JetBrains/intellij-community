package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.io.exists
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.catchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.coroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.mapLatestTimedWithLoading
import com.jetbrains.packagesearch.intellij.plugin.util.modifiedBy
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesChangesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelForEach
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignals
import com.jetbrains.packagesearch.intellij.plugin.util.throttle
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import com.jetbrains.packagesearch.intellij.plugin.util.whileLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.time.Duration

internal class PackageSearchProjectService(val project: Project) : CoroutineScope by project.lifecycleScope {

    private val retryFromErrorChannel = Channel<Unit>()
    val dataProvider = ProjectDataProvider(PackageSearchApiClient(ServerURLs.base))

    private val projectModulesLoadingFlow = MutableStateFlow(false)
    private val knownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val moduleModelsLoadingFlow = MutableStateFlow(false)
    private val allInstalledKnownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val installedPackagesStep1LoadingFlow = MutableStateFlow(false)
    private val installedPackagesStep2LoadingFlow = MutableStateFlow(false)
    private val installedPackagesDifferenceLoadingFlow = MutableStateFlow(false)
    private val packageUpgradesLoadingFlow = MutableStateFlow(false)

    private val operationExecutedChannel = Channel<List<ProjectModule>>()

    private val json = Json {
        prettyPrint = true
    }

    private val cacheDirectory = project.getProjectDataPath("pkgs/installedDependencies")
        .also { if (!it.exists()) Files.createDirectories(it) }

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
        .stateIn(this, SharingStarted.Eagerly, false)

    private val projectModulesSharedFlow = project.trustedProjectFlow.flatMapLatest { isProjectTrusted ->
        if (isProjectTrusted) project.nativeModulesChangesFlow else flowOf(emptyList())
    }
        .replayOnSignals(
            retryFromErrorChannel.receiveAsFlow().throttle(Duration.seconds(10), true),
            project.moduleChangesSignalFlow,
            timer(Duration.minutes(15))
        )
        .mapLatestTimedWithLoading("projectModulesSharedFlow", projectModulesLoadingFlow) { modules ->
            val moduleTransformations = project.moduleTransformers.map { transformer ->
                async { readAction { transformer.transformModules(project, modules) } }
            }

            val coroutinesModulesTransformations = project.coroutineModuleTransformer
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
        .shareIn(this, SharingStarted.Eagerly)

    val projectModulesStateFlow = projectModulesSharedFlow
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    val isAvailable
        get() = projectModulesStateFlow.value.isNotEmpty()

    private val knownRepositoriesFlow = timer(Duration.hours(1))
        .mapLatestTimedWithLoading("knownRepositoriesFlow", knownRepositoriesLoadingFlow) { dataProvider.fetchKnownRepositories() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#knownRepositoriesFlow",
            message = "Error while refreshing known repositories",
            fallbackValue = emptyList()
        )
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    val moduleModelsStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading(
            "moduleModelsStateFlow",
            moduleModelsLoadingFlow
        ) { projectModules -> projectModules.parallelMap { readAction { ModuleModel(it) } } }
        .catchAndLog(
            context = "${this::class.qualifiedName}#moduleModelsStateFlow",
            message = "Error while evaluating modules models",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    val projectModulesChangesFlow = projectModulesSharedFlow
        .flatMapLatest { modules ->
            project.filesChangedEventFlow.batchAtIntervals(Duration.seconds(3)) { it.flatMap { it } }
                .map { changedFilesEvents ->
                    val changedBuildFiles = changedFilesEvents.mapNotNull { it.file }
                    modules.filter { it.buildFile in changedBuildFiles }
                }
        }
        .filter { it.isNotEmpty() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#projectModulesChangesFlow",
            message = "Error while checking Modules changes",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .shareIn(this, SharingStarted.Eagerly)

    val allInstalledKnownRepositoriesFlow =
        combine(moduleModelsStateFlow, knownRepositoriesFlow) { moduleModels, repos -> moduleModels to repos }
            .replayOnSignals(projectModulesChangesFlow)
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
            .stateIn(this, SharingStarted.Eagerly, KnownRepositories.All.EMPTY)

    private val installedDependenciesExecutor = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() / 4).coerceAtLeast(2)
    ).asCoroutineDispatcher()

    val dependenciesByModuleStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading("installedPackagesStep1LoadingFlow", installedPackagesStep1LoadingFlow) {
            fetchProjectDependencies(it, cacheDirectory, json)
        }
        .modifiedBy(projectModulesChangesFlow) { installed, changedModules ->
            val (result, time) = installedPackagesDifferenceLoadingFlow.whileLoading {
                val map = installed.toMutableMap()
                changedModules.parallelForEach { map[it] = it.installedDependencies(cacheDirectory, json) }
                map
            }
            logTrace("installedPackagesStep1LoadingFlow") {
                "Took ${time} to elaborate diffs for ${changedModules.size} module" + if (changedModules.size > 1) "s" else ""
            }
            result
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#dependenciesByModuleStateFlow",
            message = "Error while evaluating installed dependencies",
            fallbackValue = emptyMap(),
            retryChannel = retryFromErrorChannel
        )
        .flowOn(installedDependenciesExecutor)
        .shareIn(this, SharingStarted.Eagerly)

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
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    val packageUpgradesStateFlow = installedPackagesStateFlow
        .mapLatestTimedWithLoading("packageUpgradesStateFlow", packageUpgradesLoadingFlow) {
            coroutineScope {
                val stableUpdates = async { computePackageUpgrades(it, true, project.packageVersionNormalizer) }
                val allUpdates = async { computePackageUpgrades(it, false, project.packageVersionNormalizer) }
                PackageUpgradeCandidates(stableUpdates.await(), allUpdates.await())
            }
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#packageUpgradesStateFlow",
            message = "Error while evaluating packages upgrade candidates",
            fallbackValue = PackageUpgradeCandidates.EMPTY,
            retryChannel = retryFromErrorChannel
        )
        .stateIn(this, SharingStarted.Eagerly, PackageUpgradeCandidates.EMPTY)

    init {
        // allows rerunning PKGS inspections on already opened files
        // when the data is finally available or changes for PackageUpdateInspection
        packageUpgradesStateFlow.filter { it.allUpgrades.allUpdates.isNotEmpty() }
            .onEach { DaemonCodeAnalyzer.getInstance(project).restart() }
            .launchIn(this)

        coroutineContext.job.invokeOnCompletion { installedDependenciesExecutor.close() }
    }

    fun notifyOperationExecuted(successes: List<ProjectModule>) {
        operationExecutedChannel.trySend(successes)
    }
}

internal inline fun <reified T, K> Flow<T>.batchAtIntervals(
    duration: Duration,
    crossinline transform: suspend (Array<T>) -> K
) = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    var job: Job? = null
    collect {
        if (job == null || job?.isCompleted == true) {
            job = launch {
                delay(duration)
                val data = mutex.withLock {
                    val d = transform(buffer.toTypedArray())
                    buffer.clear()
                    d
                }
                send(data)
            }
        }

        mutex.withLock { buffer.add(it) }
    }
}
