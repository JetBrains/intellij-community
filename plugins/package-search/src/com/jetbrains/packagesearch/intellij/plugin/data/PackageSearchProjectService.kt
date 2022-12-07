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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.getInstalledDependencies
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependenciesUsages
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.util.BackgroundLoadingBarController
import com.jetbrains.packagesearch.intellij.plugin.util.PowerSaveModeState
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo.TraceSource.SEARCH_QUERY
import com.jetbrains.packagesearch.intellij.plugin.util.catchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.combineLatest
import com.jetbrains.packagesearch.intellij.plugin.util.debounceBatch
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.loadingContainer
import com.jetbrains.packagesearch.intellij.plugin.util.map
import com.jetbrains.packagesearch.intellij.plugin.util.modifiedBy
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.moduleTransformers
import com.jetbrains.packagesearch.intellij.plugin.util.nativeModulesFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import com.jetbrains.packagesearch.intellij.plugin.util.powerSaveModeFlow
import com.jetbrains.packagesearch.intellij.plugin.util.replayOn
import com.jetbrains.packagesearch.intellij.plugin.util.send
import com.jetbrains.packagesearch.intellij.plugin.util.shareInAndCatchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.showBackgroundLoadingBar
import com.jetbrains.packagesearch.intellij.plugin.util.stateInAndCatchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.throttle
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.toolWindowManagerFlow
import com.jetbrains.packagesearch.intellij.plugin.util.trustedProjectFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import org.jetbrains.idea.packagesearch.api.PackageSearchApiClient
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Service(Level.PROJECT)
internal class PackageSearchProjectService(private val project: Project) : Disposable {

    val dataProvider = ProjectDataProvider(
        apiClient = PackageSearchApiClient(),
        packageCache = project.packageSearchProjectCachesService.installedDependencyCache
    )

    private val canShowLoadingBar = MutableStateFlow(false)

    private val restartChannel = Channel<Unit>()

    val allKnownRepositoriesFlow by timer(1.hours)
        .map(project.loadingContainer) {
            dataProvider.fetchKnownRepositories()
                .map { RepositoryModel(it.id, it.friendlyName, it.url, it) }
        }
        .stateInAndCatchAndLog(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    private val packageSearchModulesFlow by combine(
        project.trustedProjectFlow,
        ApplicationManager.getApplication().powerSaveModeFlow.map { it == PowerSaveModeState.DISABLED }
    ) { results: Array<Boolean> -> results.all { it } }
        .flatMapLatest { isPkgsEnabled -> if (isPkgsEnabled) project.nativeModulesFlow else emptyFlow() }
        .replayOn(project.moduleChangesSignalFlow)
        .map(project.loadingContainer) { nativeModules -> project.moduleTransformers.parallelMap { it.transformModules(project, nativeModules) }.flatten() }
        .catchAndLog()

    val packageSearchModulesStateFlow = packageSearchModulesFlow
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyList())

    val isAvailable
        get() = packageSearchModulesStateFlow.value.isNotEmpty()

    private val moduleChangesFlow by combine(
        packageSearchModulesFlow,
        project.filesChangedEventFlow.map { it.mapNotNull { it.file } }
    ) { modules, fileChanges ->
        modules.filter { it.buildFile in fileChanges }
    }
        .debounceBatch(1.seconds)
        .map { it.flatten().distinct() }
        .catchAndLog()

    private val repositoryChangesFlow = combine(
        moduleChangesFlow,
        allKnownRepositoriesFlow
    ) { changes, knownRepositories ->
        changes to knownRepositories
    }

    val repositoriesDeclarationsByModuleFlow by combineLatest(
        packageSearchModulesFlow,
        allKnownRepositoriesFlow,
        project.loadingContainer
    ) { modules, knownRepositories -> modules.associateWith { it.getDeclaredRepositories(knownRepositories) } }
        .modifiedBy(
            repositoryChangesFlow,
            project.loadingContainer
        ) { repositoriesDeclarationsByModule, (changes, knownRepositories) ->
            repositoriesDeclarationsByModule.toMutableMap()
                .apply { putAll(changes.associateWith { it.getDeclaredRepositories(knownRepositories) }) }
        }
        .stateInAndCatchAndLog(project.lifecycleScope, SharingStarted.Eagerly, emptyMap())

    private val declarationsChanges by moduleChangesFlow
        .map { it.associateWith { it.getDependencies() } }
        .shareInAndCatchAndLog(project.lifecycleScope, SharingStarted.Lazily)

    private val declaredDependenciesByModuleFlow by packageSearchModulesFlow
        .map(project.loadingContainer) { modules -> modules.associateWith { it.getDependencies() } }
        .modifiedBy(
            declarationsChanges,
            project.loadingContainer
        ) { declaredDependenciesByModule, changes ->
            declaredDependenciesByModule.toMutableMap().apply { putAll(changes) }
        }
        .stateInAndCatchAndLog(project.lifecycleScope, SharingStarted.Eagerly, emptyMap())

    private val remoteData by declaredDependenciesByModuleFlow
        .filter { it.isNotEmpty() }
        .take(1)
        .map(project.loadingContainer) { it.getInstalledDependencies() }
        .map(project.loadingContainer) { dataProvider.fetchInfoFor(it, TraceInfo(SEARCH_QUERY)) }
        .modifiedBy(
            declarationsChanges.map { it.getInstalledDependencies() },
            project.loadingContainer
        ) { apiResults, changes ->
            val newPackages = changes - apiResults.keys
            if (newPackages.isNotEmpty()) {
                apiResults.toMutableMap().apply { putAll(dataProvider.fetchInfoFor(newPackages, TraceInfo(SEARCH_QUERY))) }
            } else apiResults
        }
        .catchAndLog()

    val installedDependenciesFlow by combineLatest(
        flow1 = declaredDependenciesByModuleFlow,
        flow2 = remoteData,
        loadingContainer = project.loadingContainer
    ) { packageSearchModules, remoteData ->
        installedDependenciesUsages(project, packageSearchModules, remoteData)
    }.stateInAndCatchAndLog(project.lifecycleScope, SharingStarted.Eagerly, InstalledDependenciesUsages.EMPTY)

    init {
        // allows rerunning PKGS inspections on already opened files
        // when the data is finally available or changes for PackageUpdateInspection
        // or when a build file changes
        installedDependenciesFlow.flatMapLatest { packageSearchModulesFlow }
            .map { it.mapNotNull { it.buildFile?.path } }
            .filter { it.isNotEmpty() }
            .flatMapLatest { knownBuildFiles ->
                FileEditorManager.getInstance(project).openFiles
                    .filter { it.path in knownBuildFiles }.asFlow()
            }
            .mapNotNull { readAction { PsiManager.getInstance(project).findFile(it) } }
            .onEach { readAction { DaemonCodeAnalyzer.getInstance(project).restart(it) } }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)

        var controller: BackgroundLoadingBarController? = null

        project.toolWindowManagerFlow.filter { it.id == DependencyToolWindowFactory.toolWindowId }
            .take(1)
            .onEach { canShowLoadingBar.emit(true) }
            .launchIn(project.lifecycleScope)

        if (PluginEnvironment.isNonModalLoadingEnabled) {
            canShowLoadingBar
                .filter { it }
                .flatMapLatest { project.loadingContainer.loadingFlow }
                .throttle(1.seconds)
                .onEach { controller?.clear() }
                .filter { it == LoadingContainer.LoadingState.LOADING }
                .onEach {
                    controller = showBackgroundLoadingBar(
                        project = project,
                        title = PackageSearchBundle.message("toolwindow.stripe.Dependencies"),
                        upperMessage = PackageSearchBundle.message("packagesearch.ui.loading"),
                        cancellable = false,
                        isPausable = false
                    )
                }
                .launchIn(project.lifecycleScope)
        }
    }

    suspend fun restart() {
        restartChannel.send()
    }

    override fun dispose() {
        dataProvider.close()
    }
}

