package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.coroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesChangesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignals
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import kotlin.time.measureTimedValue
import kotlin.time.toDuration

internal class PackageSearchProjectService(val project: Project) : CoroutineScope by project.lifecycleScope {

    private val retryFromErrorChannel = Channel<Unit>()
    val dataProvider = ProjectDataProvider(PackageSearchApiClient(ServerURLs.base))

    private val projectModulesLoadingFlow = MutableStateFlow(false)
    private val knownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val moduleModelsLoadingFlow = MutableStateFlow(false)
    private val allInstalledKnownRepositoriesLoadingFlow = MutableStateFlow(false)
    private val installedPackagesLoadingFlow = MutableStateFlow(false)
    private val packageUpgradesLoadingFlow = MutableStateFlow(false)

    private val operationExecutedChannel = Channel<Unit>()

    val isLoadingFlow = combineTransform(
        projectModulesLoadingFlow,
        knownRepositoriesLoadingFlow,
        moduleModelsLoadingFlow,
        allInstalledKnownRepositoriesLoadingFlow,
        installedPackagesLoadingFlow,
        packageUpgradesLoadingFlow
    ) { booleans -> emit(booleans.any { it }) }
        .stateIn(this, SharingStarted.Eagerly, false)

    private val projectModulesSharedFlow = project.trustedProjectFlow.flatMapLatest { trustedState ->
        when (trustedState) {
            ThreeState.YES -> project.nativeModulesChangesFlow
            else -> flowOf(emptyList())
        }
    }
        .replayOnSignals(retryFromErrorChannel.receiveAsFlow(), project.moduleChangesSignalFlow, operationExecutedChannel.consumeAsFlow())
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
        .flowOn(Dispatchers.Default)
        .shareIn(this, SharingStarted.Eagerly)

    val projectModulesStateFlow = projectModulesSharedFlow.stateIn(this, SharingStarted.Eagerly, emptyList())

    val isAvailable
        get() = projectModulesStateFlow.value.isNotEmpty()

    private val knownRepositoriesFlow = timer(1.toDuration(TimeUnit.HOURS))
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
        ) { projectModules -> readAction { projectModules.map { ModuleModel(it) } } }
        .catchAndLog(
            context = "${this::class.qualifiedName}#moduleModelsStateFlow",
            message = "Error while evaluating modules models",
            fallbackValue = emptyList(),
            retryChannel = retryFromErrorChannel
        )
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    val allInstalledKnownRepositoriesFlow =
        combine(moduleModelsStateFlow, knownRepositoriesFlow) { moduleModels, repos -> moduleModels to repos }
            .mapLatestTimedWithLoading("allInstalledKnownRepositoriesFlow", allInstalledKnownRepositoriesLoadingFlow) { (moduleModels, repos) ->
                allKnownRepositoryModels(moduleModels, repos)
            }
            .catchAndLog(
                context = "${this::class.qualifiedName}#allInstalledKnownRepositoriesFlow",
                message = "Error while evaluating installed repositories",
                fallbackValue = KnownRepositories.All.EMPTY,
                retryChannel = retryFromErrorChannel
            )
            .stateIn(this, SharingStarted.Eagerly, KnownRepositories.All.EMPTY)

    val installedPackagesStateFlow = projectModulesSharedFlow
        .mapLatestTimedWithLoading("installedPackagesStateFlow", installedPackagesLoadingFlow) {
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
                val stableUpdates = async { computePackageUpgrades(it, true) }
                val allUpdates = async { computePackageUpgrades(it, false) }
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
    }

    fun notifyOperationExecuted() {
        operationExecutedChannel.trySend(Unit)
    }
}

private fun <T, R> Flow<T>.mapLatestTimedWithLoading(
    loggingContext: String,
    loadingFlow: MutableStateFlow<Boolean>,
    transform: suspend CoroutineScope.(T) -> R
) =
    mapLatest {
        measureTimedValue {
            loadingFlow.emit(true)
            val result = try {
                coroutineScope { transform(it) }
            } finally {
                loadingFlow.emit(false)
            }
            result
        }
    }.map {
        logInfo(loggingContext) { "Took ${it.duration.absoluteValue} to elaborate" }
        it.value
    }

private fun <T> Flow<T>.catchAndLog(context: String, message: String, fallbackValue: T, retryChannel: SendChannel<Unit>? = null) =
    catch {
        logInfo(context, it) { message }
        retryChannel?.send(Unit)
        emit(fallbackValue)
    }
