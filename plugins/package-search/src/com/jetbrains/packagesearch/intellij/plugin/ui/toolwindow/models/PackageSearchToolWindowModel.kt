package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import arrow.core.Either
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.Alarm
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.vcsUtil.VcsUtil
import com.jetbrains.packagesearch.intellij.plugin.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.SearchClient
import com.jetbrains.packagesearch.intellij.plugin.api.ServerURLs
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.PackageSearchQuery
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstallationInformation.Companion.DEFAULT_SCOPE
import com.intellij.buildsystem.model.BuildDependency
import com.intellij.buildsystem.model.DependencyConflictException
import com.intellij.buildsystem.model.DependencyNotFoundException
import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.RepositoryConflictException
import com.intellij.buildsystem.model.RepositoryNotFoundException
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.Signal
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.set

class PackageSearchToolWindowModel(val project: Project, val lifetime: Lifetime) {

    private val application = ApplicationManager.getApplication()
    private val parentDisposable = lifetime.createNestedDisposable()

    private val logger by lazy { Logger.getInstance(PackageSearchToolWindowModel::class.java) }

    private val searchAlarm = Alarm(parentDisposable)
    private val searchDelay: Long = 400 // ms
    private val lastSearchRequestId = AtomicLong()
    private val searchClient = SearchClient(ServerURLs.base)
    private val refreshContextAlarmInterval: Long = 10000 // ms
    private val refreshContextAlarm = Alarm(parentDisposable)
    private val operationsCounter = AtomicInteger(0)

    // Observables
    val isBusy = Property(false)
    val isSearching = Property(false)
    val isFetchingSuggestions = Property(false)
    val upgradeCountInContext = Property(0)

    val searchTerm = Property("")
    val searchResults = Property(mapOf<String, PackageSearchDependency>())
    private val installedPackages = Property(mapOf<String, PackageSearchDependency>())
    private val remotePackages = Property(listOf<StandardV2Package>())

    val projectModules = Property(listOf<ProjectModule>())

    val selectedProjectModule = Property<ProjectModule?>(null)
    val selectedOnlyStable = Property(false)
    val selectedOnlyMpp = Property(false)

    val selectedPackage = Property("")
    val targetPackageVersion = Property("")

    val repositories = Property(listOf<PackageSearchRepository>())
    val remoteRepositories = Property(listOf<V2Repository>())
    val selectedRemoteRepository = Property<V2Repository?>(null)
    val repositoryColorManager = RepositoryColorManager(lifetime, remoteRepositories)
    fun areMultipleRepositoriesSelected(): Boolean = selectedRemoteRepository.value == null
    fun areMultipleDistinctRepositoriesAvailable(): Boolean = repositories.value
        .mapNotNull { it.remoteInfo }
        .distinctBy { it.id }
        .count() > 1

    // UI Signals
    val focusSearchBox = Signal.Void()
    val requestRefreshContext = Signal<Boolean>()
    val searchResultsUpdated = Signal<Map<String, PackageSearchDependency>>()

    private fun startOperation() {
        isBusy.set(operationsCounter.incrementAndGet() > 0)
    }

    private fun finishOperation() {
        isBusy.set(operationsCounter.decrementAndGet() > 0)
    }

    // Implementation
    init {
        // Populate foundPackages when either:
        // - list of installed packages changes
        // - list of search results changes
        // - selected module changes
        // - selected repository changes
        installedPackages.advise(lifetime) {
            refreshFoundPackages()
        }
        remotePackages.advise(lifetime) {
            refreshFoundPackages()
        }
        selectedProjectModule.advise(lifetime) {
            refreshFoundPackages()
        }
        selectedRemoteRepository.advise(lifetime) {
            refreshFoundPackages()
        }

        // Fetch installed packages and available project modules automatically, and when requested
        val delayMillis = 250
        refreshContextAlarm.addRequest(::autoRefreshContext, delayMillis)
        requestRefreshContext.advise(lifetime) {
            refreshContext(it)
        }

        // Perform search when requested
        searchTerm.advise(lifetime) {
            maybeInvokeSearch(it, selectedOnlyStable.value, selectedOnlyMpp.value, selectedRemoteRepository.value)
        }
        selectedOnlyStable.advise(lifetime) {
            refreshFoundPackages()
            maybeInvokeSearch(searchTerm.value, it, selectedOnlyMpp.value, selectedRemoteRepository.value)
        }
        selectedOnlyMpp.advise(lifetime) {
            refreshFoundPackages()
            maybeInvokeSearch(searchTerm.value, selectedOnlyStable.value, it, selectedRemoteRepository.value)
        }
        selectedRemoteRepository.advise(lifetime) {
            refreshFoundPackages()
            maybeInvokeSearch(searchTerm.value, selectedOnlyStable.value, selectedOnlyMpp.value, it)
        }
    }

    private fun maybeInvokeSearch(searchQuery: String, onlyStable: Boolean, onlyMpp: Boolean, repository: V2Repository?) {

        val repositoryIds = repository.asList()

        if (searchQuery.length >= 2) {
            searchByName(searchQuery, onlyStable, onlyMpp, repositoryIds)
        } else {
            remotePackages.set(emptyList())
            operationsCounter.set(0)
            isBusy.set(false)
        }
    }

    @Suppress("ComplexMethod")
    private fun refreshFoundPackages() {
        startOperation()

        val currentSearchTerm = searchTerm.value
        val currentSelectedProjectModule = selectedProjectModule.value
        val currentSelectedRemoteRepository = selectedRemoteRepository.value

        val remotePackagesMatchingSearchTerm = remotePackages.value.toMutableList()

        val packagesMatchingSearchTerm = installedPackages.value
            .filter {
                it.value.isInstalled && it.value.isInstalledInProjectModule(currentSelectedProjectModule) &&
                    it.value.existsInRepository(currentSelectedRemoteRepository) &&
                    (it.value.identifier.contains(currentSearchTerm, true) ||
                        it.value.remoteInfo?.name?.contains(currentSearchTerm, true) ?: false ||
                        remotePackagesMatchingSearchTerm.any { remote -> remote.toSimpleIdentifier() == it.key })
            }.toMutableMap()

        for (installed in packagesMatchingSearchTerm) {
            val remoteMatch = remotePackagesMatchingSearchTerm.firstOrNull { it.toSimpleIdentifier() == installed.key }
            if (remoteMatch != null) {
                installed.value.remoteInfo = remoteMatch
                remotePackagesMatchingSearchTerm.remove(remoteMatch)
            }
        }

        for (remote in remotePackagesMatchingSearchTerm) {
            packagesMatchingSearchTerm[remote.toSimpleIdentifier()] = PackageSearchDependency(
                remote.groupId,
                remote.artifactId,
                remoteInfo = remote
            )
        }

        val currentProjectModule = selectedProjectModule.value
        val currentRepository = selectedRemoteRepository.value
        upgradeCountInContext.set(installedPackages.value.values.sumBy { packageSearchDependency ->
            packageSearchDependency.installationInformation.count {
                (currentProjectModule == null || it.projectModule == currentProjectModule) &&
                    (currentRepository == null || packageSearchDependency.existsInRepository(currentRepository)) &&
                        it.installedVersion.isNotBlank() &&
                        !looksLikeGradleVariable(it.installedVersion) &&
                        VersionComparatorUtil.compare(
                            it.installedVersion,
                            packageSearchDependency.getLatestAvailableVersion(selectedOnlyStable.value, currentRepository.asList())
                        ) < 0
            }
        })

        searchResults.set(packagesMatchingSearchTerm)
        searchResultsUpdated.fire(packagesMatchingSearchTerm)

        finishOperation()
    }

    private fun autoRefreshContext() {
        try {
            if (!isBusy.value) {
                refreshContext(force = false)
            }
        } finally {
            refreshContextAlarm.cancelAllRequests()
            refreshContextAlarm.addRequest(::autoRefreshContext, refreshContextAlarmInterval)
        }
    }

    private fun refreshContext(force: Boolean) {
        refreshPackagesContext()
        refreshVersionSuggestions(force)
        refreshRepositoriesContext()
    }

    private fun refreshPackagesContext() {
        val installedPackagesMap = installedPackages.value.toMutableMap()
        val projectModulesList = mutableListOf<ProjectModule>()

        // Mark all packages as "no longer installed"
        for (entry in installedPackagesMap) {
            entry.value.installationInformation.clear()
        }

        // Fetch all project modules
        val modules = ProjectModuleProvider.obtainAllProjectModulesFor(project).toList()
        for (module in modules) {
            val provider = ProjectModuleOperationProvider.forProjectModuleType(module.moduleType)
            if (provider != null) {
                // Fetch all packages that are installed in the project and re-populate our map
                val installedDependencies = provider.listDependenciesInProject(project, module.buildFile)
                for (installedDependency in installedDependencies) {
                    val item = installedPackagesMap.getOrPut(
                        installedDependency.coordinates.toSimpleIdentifier(),
                        {
                            PackageSearchDependency(
                                installedDependency.coordinates.groupId ?: "",
                                installedDependency.coordinates.artifactId ?: ""
                            )
                        })

                    item.installationInformation.add(
                        InstallationInformation(
                            projectModule = module,
                            installedVersion = installedDependency.coordinates.version ?: "",
                            rawScope = installedDependency.scope
                        )
                    )
                }

                // Update list of project modules
                projectModulesList.add(module)
            }
        }

        // Any packages that are no longer installed?
        installedPackagesMap.filterNot { it.value.isInstalled }
            .keys
            .forEach { keyToRemove -> installedPackagesMap.remove(keyToRemove) }

        installedPackages.set(installedPackagesMap)
        projectModules.set(projectModulesList)
    }

    private fun refreshVersionSuggestions(force: Boolean) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).allowCheckForPackageUpgrades) return

        application.executeOnPooledThread {
            val installedPackagesToCheck = installedPackages.value
                .filter { force || it.value.remoteInfo == null }.values

            if (installedPackagesToCheck.any()) isFetchingSuggestions.set(true)

            installedPackagesToCheck.chunked(size = 25).forEach { chunk ->
                val result = searchClient.packagesByRange(chunk.map { "${it.groupId}:${it.artifactId}" })
                if (result.isRight()) {
                    (result as Either.Right).b.packages?.forEach {
                        val simpleIdentifier = it.toSimpleIdentifier()
                        val installedPackage = installedPackages.value[simpleIdentifier]
                        if (installedPackage != null && simpleIdentifier == installedPackage.identifier && installedPackage.remoteInfo == null) {
                            installedPackage.remoteInfo = it
                        }
                    }
                }
            }

            application.invokeLater {
                refreshFoundPackages()
                isFetchingSuggestions.set(false)
            }
        }
    }

    private fun refreshRepositoriesContext() {
        // Fetch remote repositories
        refreshRemoteRepositoriesContext()

        // Fetch configured repositories
        val repositoriesList = mutableListOf<PackageSearchRepository>()
        val modules = ProjectModuleProvider.obtainAllProjectModulesFor(project).toList()
        for (module in modules) {
            val provider = ProjectModuleOperationProvider.forProjectModuleType(module.moduleType)
            if (provider != null) {
                // Fetch all repositories that are configured
                val configuredRepositories = provider.listRepositoriesInProject(project, module.buildFile)
                for (configuredRepository in configuredRepositories) {
                    repositoriesList.add(
                        PackageSearchRepository(
                            configuredRepository.id,
                            configuredRepository.name,
                            configuredRepository.url,
                            module,
                            remoteRepositories.value.firstOrNull { it.isEquivalentTo(configuredRepository) }
                        )
                    )
                }
            }
        }

        repositories.set(repositoriesList)
    }

    private fun refreshRemoteRepositoriesContext() {
        // NOTE: We'll do this only when no repositories are present yet, as we don't expect the remote to update during an IDE session.
        if (remoteRepositories.value.isNotEmpty()) return

        startOperation()

        application.executeOnPooledThread {
            val entries = searchClient.repositories()

            application.invokeLater {

                entries.fold({
                    PackageSearchEventsLogger.onGetRepositoriesFailed(project)
                }, {
                    if (it.repositories != null) {
                        remoteRepositories.set(it.repositories)
                    }
                })

                finishOperation()
            }
        }
    }

    private fun searchByName(query: String, onlyStable: Boolean, onlyMpp: Boolean, repositoryIds: List<String>) {
        val searchRequestId = lastSearchRequestId.incrementAndGet()

        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest({
            if (searchRequestId != lastSearchRequestId.get()) return@addRequest

            startOperation()
            isSearching.set(true)
            PackageSearchEventsLogger.onSearchRequest(project, query)

            application.executeOnPooledThread {
                val packageSearchQuery = PackageSearchQuery(query)
                val entries = searchClient.packagesByQuery(packageSearchQuery, onlyStable, onlyMpp, repositoryIds)

                application.invokeLater {
                    if (searchRequestId != lastSearchRequestId.get()) {
                        isSearching.set(false)
                        finishOperation()
                        return@invokeLater
                    }

                    entries.fold(
                        {
                            PackageSearchEventsLogger.onSearchFailed(project, query)

                            NotificationGroup.balloonGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
                                .createNotification(
                                    PackageSearchBundle.message("packagesearch.title"),
                                    PackageSearchBundle.message("packagesearch.search.client.searching.failed"),
                                    it,
                                    NotificationType.ERROR
                                )
                                .notify(project)
                        },
                        {
                            PackageSearchEventsLogger.onSearchResponse(project, query, it.packages!!)

                            remotePackages.set(it.packages)
                            isSearching.set(false)
                            finishOperation()
                        })
                }
            }
        }, searchDelay)
    }

    fun preparePackageOperationTargetsFor(projectModules: Iterable<ProjectModule>, repository: V2Repository?) =
        installedPackages.value
            .filter { it.value.isInstalled && (repository == null || it.value.existsInRepository(repository)) }
            .values.flatMap { packageSearchDependency ->
                packageSearchDependency.installationInformation.filter { f -> projectModules.any { it == f.projectModule } }
                    .map { installedDependency ->
                        // Package is installed at least once, add all installations to our list
                        PackageOperationTarget(
                            installedDependency.projectModule,
                            installedDependency.projectModule.name,
                            packageSearchDependency,
                            installedDependency.installedVersion,
                            installedDependency.rawScope,
                            PackageOperationTargetScope(
                                CollectionComboBoxModel(
                                    installedDependency.projectModule.moduleType.scopes(project).union(listOf(installedDependency.scope)).distinct(),
                                    installedDependency.scope
                                )
                            )
                        )
                    }
        }

    fun preparePackageOperationTargetsFor(
        projectModules: Iterable<ProjectModule>,
        selectedDependency: PackageSearchDependency,
        repository: V2Repository?
    ) =
        projectModules.flatMap { projectModule ->
            selectedDependency.installationInformation
                .filter { it.projectModule == projectModule && (repository == null || selectedDependency.existsInRepository(repository)) }
                .map { installedDependency ->
                    // Package is installed at least once, add all installations to our list
                    PackageOperationTarget(
                        projectModule,
                        projectModule.name,
                        selectedDependency,
                        installedDependency.installedVersion,
                        installedDependency.rawScope,
                        PackageOperationTargetScope(
                            CollectionComboBoxModel(
                                projectModule.moduleType.scopes(project).union(listOf(installedDependency.scope)).distinct(),
                                installedDependency.scope
                            )
                        )
                    )
                }.ifEmpty {
                    // Package is not installed, add a placeholder so we can install it if desired
                    listOf(
                        PackageOperationTarget(
                            projectModule,
                            projectModule.name,
                            selectedDependency,
                            "",
                            "",
                            PackageOperationTargetScope(
                                CollectionComboBoxModel(
                                    projectModule.moduleType.scopes(project),
                                    projectModule.moduleType.defaultScope(project)
                                )
                            )
                        )
                    )
                }
        }

    fun executeOperations(executableOperations: List<ExecutablePackageOperation>) {
        ProgressManager.getInstance().runBackgroundable(
            project = project,
            title = PackageSearchBundle.message("packagesearch.ui.toolwindow.operation.title"),
            backgroundOption = PerformInBackgroundOption.DEAF
        ) { indicator ->
            indicator.isIndeterminate = true

            PackageSearchEventsLogger.onProjectInfo(
                project,
                ModuleManager.getInstance(project).modules,
                executableOperations.map { it.operationTarget.projectModule }.distinct()
            )

            startOperation()
            val currentSelectedPackage = selectedPackage.value
            try {
                for (executableOperation in executableOperations) {
                    processOperation(executableOperation)
                }
            } finally {
                finishOperation()
            }

            refreshContext(force = false)
            selectedPackage.set(currentSelectedPackage)
        }
    }

    @Suppress("LongMethod")
    private fun processOperation(executableOperation: ExecutablePackageOperation) {
        val operation = executableOperation.operation
        val operationTarget = executableOperation.operationTarget

        val projectModuleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(operationTarget.projectModule.moduleType)
        val virtualFile = operationTarget.projectModule.buildFile

        val operationFailures = if (operation.packageOperationType == PackageOperationType.REMOVE) {
            projectModuleOperationProvider?.removeDependenciesFromProject(
                DependencyOperationMetadata(
                    operationTarget.projectModule,
                    operationTarget.packageSearchDependency.groupId,
                    operationTarget.packageSearchDependency.artifactId,
                    executableOperation.targetVersion
                        ?: operationTarget.packageSearchDependency.getLatestAvailableVersion(
                            selectedOnlyStable.value, selectedRemoteRepository.value.asList()),
                    operationTarget.installedScopeIfNotDefault
                ),
                project, virtualFile
            )
        } else {
            PackageSearchEventsLogger.onPackageInstallHit(
                project,
                operationTarget.projectModule.buildSystemType,
                operationTarget.packageSearchDependency.remoteInfo,
                remotePackages.value
            )

            val scope = operationTarget.targetScope.getSelectedScope().let {
                if (it == DEFAULT_SCOPE) return@let null else return@let it
            }

            val operationTargetVersion = executableOperation.targetVersion
                ?: operationTarget.packageSearchDependency.getLatestAvailableVersion(
                    selectedOnlyStable.value, selectedRemoteRepository.value.asList())

            val operationTargetVersionRepository = remoteRepositories.value
                .firstOrNull { remoteRepository ->
                    val remoteRepositoryIds = operationTarget.packageSearchDependency.remoteInfo?.versions
                        ?.firstOrNull { it.version.equals(operationTargetVersion, true) }
                        ?.repositoryIds ?: emptyList()

                    remoteRepositoryIds.contains(remoteRepository.id)
                }

            val addRepositoriesResult = if (operationTargetVersionRepository != null) {
                projectModuleOperationProvider?.addRepositoriesToProject(
                    UnifiedDependencyRepository(
                        operationTargetVersionRepository.id,
                        operationTargetVersionRepository.localizedName(),
                        operationTargetVersionRepository.url
                    ),
                    project, virtualFile
                )
            } else null

            val addDependenciesResult =
                projectModuleOperationProvider?.addDependenciesToProject(
                    DependencyOperationMetadata(
                        operationTarget.projectModule,
                        operationTarget.packageSearchDependency.groupId,
                        operationTarget.packageSearchDependency.artifactId,
                        operationTargetVersion,
                        scope
                    ),
                    project, virtualFile
                )

            (addRepositoriesResult ?: emptyList()) + (addDependenciesResult ?: emptyList())
        }

        when {
            operationFailures == null -> {
                displayError(
                    project = project,
                    message = PackageSearchBundle.message("packagesearch.add.dependency.unknown.module.type"),
                    errorType = NotificationType.ERROR
                )
            }
            operationFailures.isNotEmpty() -> operationFailures.forEach {
                val errorData = prepareError(it)
                displayError(project, errorData.message, errorData.notificationType)
            }
        }

        // Reformat/refresh even if there were failures, as part of the executableOperations may have succeeded
        reformatCode(project, virtualFile)
        projectModuleOperationProvider?.refreshProject(project, virtualFile)
    }

    private fun prepareError(error: OperationFailure<*>): ErrorData =
        when (error.error) {
            is DependencyConflictException ->
                ErrorData(PackageSearchBundle.message(
                    "packagesearch.add.dependency.error.dependency.already.exists",
                    (error.item as BuildDependency).displayName
                ), NotificationType.INFORMATION)
            is RepositoryConflictException ->
                ErrorData(PackageSearchBundle.message(
                    "packagesearch.add.dependency.error.repository.already.exists",
                    error.item.toString()
                ), NotificationType.INFORMATION)
            is DependencyNotFoundException ->
                ErrorData(PackageSearchBundle.message(
                    "packagesearch.add.dependency.error.dependency.not.found",
                    (error.item as BuildDependency).displayName
                ), NotificationType.ERROR)
            is RepositoryNotFoundException ->
                ErrorData(PackageSearchBundle.message(
                    "packagesearch.add.dependency.error.repository.not.found",
                    error.item.toString()
                ), NotificationType.ERROR)
            else -> {
                logger.error(PackageSearchBundle.message("packagesearch.add.dependency.error.unknown"), error.error)
                ErrorData(
                    PackageSearchBundle.message("packagesearch.add.dependency.error.unknown"),
                    NotificationType.ERROR
                )
            }
        }

    private data class ErrorData(@Nls val message: String, val notificationType: NotificationType)

    private fun displayError(project: Project, @Nls message: String, errorType: NotificationType = NotificationType.ERROR) {
        NotificationGroup.balloonGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
            .createNotification(
                PackageSearchBundle.message("packagesearch.title"),
                PackageSearchBundle.message("packagesearch.add.dependency.dialog.title"),
                message,
                errorType
            ).notify(project)
    }

    private fun reformatCode(project: Project, file: VirtualFile) {
        application.invokeLater {
            application.runWriteAction {
                val onlyChangedText = VcsUtil.isFileUnderVcs(project, file.path)
                PsiManager.getInstance(project).findFile(file)?.let {
                    ReformatCodeProcessor(it, onlyChangedText).run()
                }
            }
        }
    }
}

private fun UnifiedCoordinates.toSimpleIdentifier(): String = "$groupId:$artifactId".toLowerCase()

private fun ProgressManager.runBackgroundable(
  project: Project,
  @ProgressTitle title: String,
  cancelable: Boolean = false,
  backgroundOption: PerformInBackgroundOption = PerformInBackgroundOption.ALWAYS_BACKGROUND,
  action: (indicator: ProgressIndicator) -> Unit
) = run(
    object : Task.Backgroundable(project, title, cancelable, backgroundOption) {
        override fun run(indicator: ProgressIndicator) {
            action(indicator)
        }
    })

private fun PackageSearchDependency.isInstalledInProjectModule(projectModule: ProjectModule?): Boolean {
    return projectModule == null ||
        this.installationInformation.any { installationInformation ->
            installationInformation.projectModule == projectModule
        }
}

private fun PackageSearchDependency.existsInRepository(repository: V2Repository?): Boolean {
    return repository == null ||
        this.installationInformation.any { installationInformation ->
            val installedVersion =
                if (installationInformation.installedVersion.isBlank() ||
                    looksLikeGradleVariable(installationInformation.installedVersion)) {

                    this.remoteInfo?.latestVersion
                } else {
                    this.remoteInfo?.versions?.firstOrNull { v ->
                        v.version.equals(installationInformation.installedVersion, true)
                    }
                }

            installedVersion?.repositoryIds != null &&
                installedVersion.repositoryIds.contains(repository.id)
        }
}
