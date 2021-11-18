package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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
import com.jetbrains.packagesearch.intellij.plugin.util.batchAtIntervals
import com.jetbrains.packagesearch.intellij.plugin.util.catchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.coroutineModuleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.mapLatestTimedWithLoading
import com.jetbrains.packagesearch.intellij.plugin.util.modifiedBy
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesChangesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import com.jetbrains.packagesearch.intellij.plugin.util.parallelUpdatedKeys
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignals
import com.jetbrains.packagesearch.intellij.plugin.util.throttle
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import com.jetbrains.packagesearch.intellij.plugin.util.whileLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.Nls
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

    private val json = Json { prettyPrint = true }

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

    val projectModulesChangesFlow = combine(
        projectModulesSharedFlow,
        project.filesChangedEventFlow.map { it.mapNotNull { it.file } }
    ) { modules, changedBuildFiles -> modules.filter { it.buildFile in changedBuildFiles } }
        .filter { it.isNotEmpty() }
        .let { merge(it, operationExecutedChannel.consumeAsFlow()) }
        .batchAtIntervals(Duration.seconds(1))
        .map { it.flatMap { it } }
        .catchAndLog(
            context = "${this::class.qualifiedName}#projectModulesChangesFlow",
            message = "Error while checking Modules changes",
            fallbackValue = emptyList()
        )
        .shareIn(this, SharingStarted.Eagerly)

    val moduleModelsStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading(
            loggingContext = "moduleModelsStateFlow",
            loadingFlow = moduleModelsLoadingFlow
        ) { projectModules -> projectModules.parallelMap { it to ModuleModel(it) }.toMap() }
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
        .stateIn(this, SharingStarted.Eagerly, emptyList())

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
                installed.parallelUpdatedKeys(changedModules) { it.installedDependencies(cacheDirectory, json) }
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
                val stableUpdates = async { computePackageUpgrades(it, true, packageVersionNormalizer) }
                val allUpdates = async { computePackageUpgrades(it, false, packageVersionNormalizer) }
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

//        var controller: BackgroundLoadingBarController? = null
//
//        isLoadingFlow.onEach { isLoading ->
//            if (isLoading) {
//                controller = runBackgroundLoadingBar(
//                    project,
//                    PackageSearchBundle.message("toolwindow.stripe.Dependencies"),
//                    PackageSearchBundle.message("packagesearch.ui.loading")
//                )
//            } else {
//                controller?.clear()
//            }
//        }.launchIn(this)
    }

    fun notifyOperationExecuted(successes: List<ProjectModule>) {
        operationExecutedChannel.trySend(successes)
    }
}

private fun CoroutineScope.runBackgroundLoadingBar(
    project: Project,
    @Nls title: String,
    @Nls upperMessage: String
): BackgroundLoadingBarController {
    val syncSignal = Mutex(true)
    val upperMessageChannel = Channel<String>()
    val lowerMessageChannel = Channel<String>()
    val externalScopeJob = coroutineContext.job
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title) {
        override fun run(indicator: ProgressIndicator) {
            runBlocking {
                upperMessageChannel.consumeAsFlow().onEach { indicator.text = it }.launchIn(this)
                lowerMessageChannel.consumeAsFlow().onEach { indicator.text2 = it }.launchIn(this)
                indicator.text = upperMessage // ??? why does it work?
                val internalJob = launch {
                    syncSignal.lock()
                    logWarn { "lock released" }
                }
                select<Unit> {
                    internalJob.onJoin { }
                    externalScopeJob.onJoin { internalJob.cancel() }
                }
                upperMessageChannel.close()
                lowerMessageChannel.close()
            }
        }
    })
    return BackgroundLoadingBarController(syncSignal, upperMessageChannel, lowerMessageChannel)
}

class BackgroundLoadingBarController(
    private val syncMutex: Mutex,
    val upperMessageChannel: SendChannel<String>,
    val lowerMessageChannel: SendChannel<String>
) {

    fun clear() = runCatching { syncMutex.unlock() }.getOrElse { }
}
