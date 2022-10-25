/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.dependencytoolwindow.DependencyToolWindowFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.BackgroundLoadingBarController
import com.jetbrains.packagesearch.intellij.plugin.util.PowerSaveModeState
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo.TraceSource.INSTALLED_PACKAGES
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo.TraceSource.PACKAGE_UPGRADES
import com.jetbrains.packagesearch.intellij.plugin.util.batchAtIntervals
import com.jetbrains.packagesearch.intellij.plugin.util.catchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.combineLatest
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.mapLatestTimedWithLoading
import com.jetbrains.packagesearch.intellij.plugin.util.modifiedBy
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import com.jetbrains.packagesearch.intellij.plugin.util.parallelUpdatedKeys
import com.jetbrains.packagesearch.intellij.plugin.util.pauseOn
import com.jetbrains.packagesearch.intellij.plugin.util.powerSaveModeFlow
import com.jetbrains.packagesearch.intellij.plugin.util.replayOnSignals
import com.jetbrains.packagesearch.intellij.plugin.util.send
import com.jetbrains.packagesearch.intellij.plugin.util.showBackgroundLoadingBar
import com.jetbrains.packagesearch.intellij.plugin.util.throttle
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.toolWindowManagerFlow
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import com.jetbrains.packagesearch.intellij.plugin.util.trySend
import com.jetbrains.packagesearch.intellij.plugin.util.whileLoading
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.Json
import org.jetbrains.idea.packagesearch.api.PackageSearchApiClient
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class PackageSearchProjectService(private val project: Project) : Disposable {

    private val retryFromErrorChannel = Channel<Unit>()
    private val apiClient = PackageSearchApiClient()

    val dataProvider = ProjectDataProvider(
        apiClient,
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
    internal val editingFilesState = MutableStateFlow(false)

    private val computationInterruptedChannel = Channel<Unit>()
    private val computationStartedChannel = Channel<Unit>()

    private val computationAllowedState = channelFlow {
        computationInterruptedChannel.consumeAsFlow()
            .onEach { send(false) }
            .launchIn(this)
        computationStartedChannel.consumeAsFlow()
            .onEach { send(true) }
            .launchIn(this)
    }.stateIn(project.lifecycleScope, SharingStarted.Eagerly, true)

    val isComputationAllowed
        get() = computationAllowedState.value

    private val canShowLoadingBar = MutableStateFlow(false)

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
        packageUpgradesLoadingFlow,
        editingFilesState
    ) { booleans -> emit(booleans.any { it }) }
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, false)

    private val projectModulesSharedFlow =
        combine(
            project.trustedProjectFlow,
            ApplicationManager.getApplication().powerSaveModeFlow.map { it == PowerSaveModeState.ENABLED },
            computationAllowedState
        ) { isProjectTrusted, powerSaveModeEnabled, computationEnabled ->
            isProjectTrusted && !powerSaveModeEnabled && computationEnabled
        }
            .pauseOn(editingFilesState.map { !it })
            .flatMapLatest { isPkgsEnabled -> if (isPkgsEnabled) project.nativeModulesFlow else flowOf(emptyList()) }
            .replayOnSignals(
                retryFromErrorChannel.receiveAsFlow().throttle(10.seconds),
                project.moduleChangesSignalFlow,
            )
            .mapLatestTimedWithLoading("projectModulesSharedFlow", projectModulesLoadingFlow) { modules ->
                project.moduleTransformers
                    .map { async { it.transformModules(project, modules) } }
                    .awaitAll()
                    .flatten()
            }
            .catchAndLog(
                context = "${this::class.qualifiedName}#projectModulesSharedFlow",
                message = "Error while elaborating latest project modules"
            )
            .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    val projectModulesStateFlow = projectModulesSharedFlow.stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val isAvailable
        get() = projectModulesStateFlow.value.isNotEmpty()

    private val knownRepositoriesFlow = timer(1.hours)
        .pauseOn(editingFilesState.map { !it })
        .mapLatestTimedWithLoading("knownRepositoriesFlow", knownRepositoriesLoadingFlow) { dataProvider.fetchKnownRepositories() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#knownRepositoriesFlow",
            message = "Error while refreshing known repositories"
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    private val buildFileChangesFlow = combine(
        projectModulesSharedFlow,
        project.filesChangedEventFlow.map { it.mapNotNull { it.file } }
    ) { modules, changedBuildFiles -> modules.filter { it.buildFile in changedBuildFiles } }
        .pauseOn(editingFilesState.map { !it })
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    private val projectModulesChangesFlow = merge(
        buildFileChangesFlow.filter { it.isNotEmpty() },
        operationExecutedChannel.consumeAsFlow()
    )
        .pauseOn(editingFilesState.map { !it })
        .batchAtIntervals(1.seconds)
        .map { it.flatMap { it }.distinct() }
        .catchAndLog(
            context = "${this::class.qualifiedName}#projectModulesChangesFlow",
            message = "Error while checking Modules changes"
        )
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    val moduleModelsStateFlow = projectModulesSharedFlow
        .pauseOn(editingFilesState.map { !it })
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
            message = "Error while evaluating modules models"
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val allInstalledKnownRepositoriesStateFlow =
        combine(moduleModelsStateFlow, knownRepositoriesFlow) { moduleModels, repos -> moduleModels to repos }
            .pauseOn(editingFilesState.map { !it })
            .mapLatestTimedWithLoading(
                loggingContext = "allInstalledKnownRepositoriesFlow",
                loadingFlow = allInstalledKnownRepositoriesLoadingFlow
            ) { (moduleModels, repos) ->
                allKnownRepositoryModels(moduleModels, repos)
            }
            .catchAndLog(
                context = "${this::class.qualifiedName}#allInstalledKnownRepositoriesFlow",
                message = "Error while evaluating installed repositories"
            )
            .stateIn(project.lifecycleScope, SharingStarted.Eagerly, KnownRepositories.All.EMPTY)

    val dependenciesByModuleStateFlow = projectModulesSharedFlow
        .pauseOn(editingFilesState.map { !it })
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
            message = "Error while evaluating installed dependencies"
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyMap())

    val installedPackagesStateFlow = dependenciesByModuleStateFlow
        .pauseOn(editingFilesState.map { !it })
        .mapLatestTimedWithLoading("installedPackagesStep2LoadingFlow", installedPackagesStep2LoadingFlow) {
            installedPackages(
                dependenciesByModule = it,
                project = project,
                dataProvider = dataProvider,
                traceInfo = TraceInfo(INSTALLED_PACKAGES)
            )
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#installedPackagesStateFlow",
            message = "Error while evaluating installed packages"
        )
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val packageUpgradesStateFlow = combineLatest(
        installedPackagesStateFlow,
        moduleModelsStateFlow,
        knownRepositoriesFlow
    ) { (installedPackages, moduleModels, repos) ->
            val trace = TraceInfo(PACKAGE_UPGRADES)
            availableUpgradesLoadingFlow.emit(true)
            val result = PackageUpgradeCandidates(
                computePackageUpgrades(
                    installedPackages = installedPackages,
                    onlyStable = false,
                    normalizer = packageVersionNormalizer,
                    repos = allKnownRepositoryModels(moduleModels, repos),
                    nativeModulesMap = moduleModels.associateBy { it.projectModule },
                    trace = trace
                )
            )
            availableUpgradesLoadingFlow.emit(false)
            result
        }
        .catchAndLog(
            context = "${this::class.qualifiedName}#packageUpgradesStateFlow",
            message = "Error while evaluating packages upgrade candidates"
        )
        .stateIn(project.lifecycleScope, SharingStarted.Lazily, PackageUpgradeCandidates.EMPTY)

    init {
        // allows rerunning PKGS inspections on already opened files
        // when the data is finally available or changes for PackageUpdateInspection
        // or when a build file changes
        packageUpgradesStateFlow.throttle(5.seconds)
            .map { projectModulesStateFlow.value.mapNotNull { it.buildFile?.path }.toSet() }
            .filter { it.isNotEmpty() }
            .pauseOn(editingFilesState.map { !it })
            .flatMapLatest { knownBuildFiles ->
                FileEditorManager.getInstance(project).openFiles
                    .filter { it.path in knownBuildFiles }.asFlow()
            }
            .mapNotNull { readAction { PsiManager.getInstance(project).findFile(it) } }
            .onEach { readAction { DaemonCodeAnalyzer.getInstance(project).restart(it) } }
            .catchAndLog("${this::class.qualifiedName}#inspectionsRestart")
            .launchIn(project.lifecycleScope)

        var controller: BackgroundLoadingBarController? = null

        project.toolWindowManagerFlow
            .filter { it.id == DependencyToolWindowFactory.toolWindowId }
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
                        project = project,
                        title = PackageSearchBundle.message("toolwindow.stripe.Dependencies"),
                        upperMessage = PackageSearchBundle.message("packagesearch.ui.loading"),
                        cancellable = true
                    ).also {
                        it.addOnComputationInterruptedCallback {
                            computationInterruptedChannel.trySend()
                        }
                    }
                }.launchIn(project.lifecycleScope)
        }
    }

    fun notifyOperationExecuted(successes: List<ProjectModule>) {
        operationExecutedChannel.trySend(successes)
    }

    suspend fun restart() {
        computationStartedChannel.send()
        retryFromErrorChannel.send()
    }

    fun resumeComputation() {
        computationStartedChannel.trySend()
    }

    override fun dispose() {
        apiClient.close()
    }
}
