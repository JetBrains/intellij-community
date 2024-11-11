// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.normalizePackageName
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.packagesByRepository
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
class PyPackagingToolWindowService(val project: Project, val serviceScope: CoroutineScope) : Disposable {
  private var toolWindowPanel: PyPackagingToolWindowPanel? = null
  lateinit var manager: PythonPackageManager
  private var installedPackages: Map<String, InstalledPackage> = emptyMap()
  internal var currentSdk: Sdk? = null
  private var searchJob: Job? = null
  private var currentQuery: String = ""

  private val invalidRepositories: List<PyInvalidRepositoryViewData>
    get() = service<PyPackageRepositories>().invalidRepositories.map(::PyInvalidRepositoryViewData)


  fun initialize(toolWindowPanel: PyPackagingToolWindowPanel) {
    this.toolWindowPanel = toolWindowPanel
    serviceScope.launch(Dispatchers.IO) {
      service<PyPIPackageRanking>().reload()
      initForSdk(project.modules.firstOrNull()?.pythonSdk)
    }
    subscribeToChanges()
  }

  suspend fun detailsForPackage(selectedPackage: DisplayablePackage): PythonPackageDetails = withContext(Dispatchers.IO) {
    PythonPackagesToolwindowStatisticsCollector.requestDetailsEvent.log(project)
    val spec = selectedPackage.repository.createPackageSpecification(selectedPackage.name)
    manager.repositoryManager.getPackageDetails(spec)
  }


  fun handleSearch(query: String) {
    val prevSelected = toolWindowPanel?.getSelectedPackage()

    currentQuery = query
    if (query.isNotEmpty()) {
      searchJob?.cancel()
      searchJob = serviceScope.launch {
        val installed = installedPackages.values.filter { pkg ->
          when {
            pkg.instance is CondaPackage && !pkg.instance.installedWithPip -> StringUtil.containsIgnoreCase(pkg.name, query)
            else -> StringUtil.containsIgnoreCase(normalizePackageName(pkg.name), normalizePackageName(query))
          }
        }

        val packagesFromRepos = manager.repositoryManager.searchPackages(query).map {
          sortPackagesForRepo(it.value, query, it.key)
        }.toList()

        if (isActive) {
          withContext(Dispatchers.Main) {
            toolWindowPanel?.showSearchResult(installed, packagesFromRepos + invalidRepositories)
            prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
          }
        }
      }
    }
    else {
      val packagesByRepository = manager.repositoryManager.packagesByRepository().map { (repository, packages) ->
        val shownPackages = packages.asSequence().limitResultAndFilterOutInstalled(repository)
        PyPackagesViewData(repository, shownPackages, moreItems = packages.size - PACKAGES_LIMIT)
      }.toList()

      toolWindowPanel?.resetSearch(installedPackages.values.toList(), packagesByRepository + invalidRepositories, currentSdk)
      prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
    }
  }

  suspend fun installPackage(specification: PythonPackageSpecification, options: List<String> = emptyList()) {
    PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)

    val result = runPackagingOperationOrShowErrorDialog(manager.sdk, message("python.new.project.install.failed.title", specification.name), specification.name) {
      manager.installPackage(specification, options)
    }
    if (result.isSuccess) showPackagingNotification(message("python.packaging.notification.installed", specification.name))
  }

  suspend fun deletePackage(selectedPackage: InstalledPackage) {
    PythonPackagesToolwindowStatisticsCollector.uninstallPackageEvent.log(project)
    val result =  runPackagingOperationOrShowErrorDialog(manager.sdk, message("python.packaging.operation.failed.title")) {
      manager.uninstallPackage(selectedPackage.instance)
    }
    if (result.isSuccess) showPackagingNotification(message("python.packaging.notification.deleted", selectedPackage.name))
  }

  suspend fun updatePackage(specification: PythonPackageSpecification) {
    val result = runPackagingOperationOrShowErrorDialog(manager.sdk, message("python.packaging.notification.update.failed", specification.name), specification.name) {
      manager.updatePackage(specification)
    }
    if (result.isSuccess) showPackagingNotification(message("python.packaging.notification.updated", specification.name, specification.versionSpecs))
  }

  internal suspend fun initForSdk(sdk: Sdk?) {
    if (sdk == null) {
      toolWindowPanel?.packageListController?.setLoadingState(false)
    }
    if (sdk == currentSdk)
      return

    withContext(Dispatchers.EDT) {
      toolWindowPanel?.startLoadingSdk()
    }
    val previousSdk = currentSdk
    currentSdk = sdk
    if (sdk == null) {
      return
    }
    manager = PythonPackageManager.forSdk(project, currentSdk!!)
    manager.repositoryManager.initCaches()
    runPackagingOperationOrShowErrorDialog(sdk, message("python.packaging.operation.failed.title")) {
      manager.reloadPackages()
    }

    withContext(Dispatchers.Main) {
      toolWindowPanel?.contentVisible = currentSdk != null
      if (currentSdk == null || currentSdk != previousSdk) {
        toolWindowPanel?.setEmpty()
      }
    }
  }

  private fun subscribeToChanges() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        if (currentSdk == sdk) serviceScope.launch(Dispatchers.IO) {
          refreshInstalledPackages()
        }
      }
    })
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        serviceScope.launch(Dispatchers.IO) {
          initForSdk(project.modules.firstOrNull()?.pythonSdk)
        }
      }
    })

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        event.newFile?.let { newFile ->
          serviceScope.launch {
            val sdk = readAction {
              val module = ModuleUtilCore.findModuleForFile(newFile, project)
              PythonSdkUtil.findPythonSdk(module)
            } ?: return@launch
            initForSdk(sdk)
          }
        }
      }
    })
  }


  suspend fun refreshInstalledPackages() {
    val packages = manager.installedPackages.map {
      val repository = installedPackages.values.find { pkg -> pkg.name == it.name }?.repository ?: PyEmptyPackagePackageRepository
      InstalledPackage(it, repository, null)
    }

    installedPackages = packages.associateBy { it.name }

    withContext(Dispatchers.Main) {
      handleSearch(query = currentQuery)
    }

    withContext(Dispatchers.Default) {
      launch {
        calculateLatestVersionForInstalledPackages()
        withContext(Dispatchers.Main) {
          handleSearch(query = currentQuery)
        }
      }
    }
  }

  private suspend fun calculateLatestVersionForInstalledPackages() {
    val proccessPackages = installedPackages

    val updatedPackages = withContext(Dispatchers.Main) {
      val jobs = proccessPackages.entries.chunked(5).map { chunk ->
        async(Dispatchers.IO) {
          chunk.map { (name, pyPackage) ->
            name to calculatePyPackageLatestVersion(pyPackage)
          }
        }
      }
      val results = jobs.awaitAll()
      results.flatten().toMap()
    }

    installedPackages = installedPackages.map {
      val newVersion = updatedPackages[it.key]
      it.key to it.value.withNextVersion(newVersion)
    }.toMap()
  }

  private suspend fun calculatePyPackageLatestVersion(pyPackage: InstalledPackage): PyPackageVersion? {
    if (pyPackage.nextVersion != null)
      return pyPackage.nextVersion

    try {
      val specification = pyPackage.repository.createPackageSpecification(pyPackage.name)
      val latestVersion = manager.repositoryManager.getLatestVersion(specification)
      val currentVersion = PyPackageVersionNormalizer.normalize(pyPackage.instance.version)

      val upgradeTo = if (latestVersion != null && currentVersion != null &&
                          PyPackageVersionComparator.compare(latestVersion, currentVersion) > 0) {
        latestVersion
      }
      else {
        null
      }
      return upgradeTo
    }
    catch (t: Throwable) {
      thisLogger().warn("Cannot get version for ${pyPackage.instance.name}", t)
      return null
    }
  }

  private suspend fun showPackagingNotification(text: @Nls String) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("PythonPackages")
      .createNotification(text, NotificationType.INFORMATION)

    withContext(Dispatchers.Main) {
      notification.notify(project)
    }
  }

  private fun sortPackagesForRepo(
    packageNames: List<String>,
    query: String,
    repository: PyPackageRepository,
    skipItems: Int = 0,
  ): PyPackagesViewData {

    val comparator = createNameComparator(query, repository.repositoryUrl ?: "")

    val shownPackages = packageNames.asSequence()
      .sortedWith(comparator)
      .limitResultAndFilterOutInstalled(repository, skipItems)
    val exactMatch = shownPackages.indexOfFirst { StringUtil.equalsIgnoreCase(it.name, query) }
    return PyPackagesViewData(repository, shownPackages, exactMatch, packageNames.size - shownPackages.size)
  }


  override fun dispose() {
    searchJob?.cancel()
    serviceScope.cancel()
  }


  fun reloadPackages() {
    serviceScope.launch(Dispatchers.IO) {
      withBackgroundProgress(project, message("python.packaging.loading.packages.progress.text"), cancellable = false) {
        reportRawProgress {
          runPackagingOperationOrShowErrorDialog(manager.sdk, message("python.packaging.operation.failed.title")) {
            manager.reloadPackages()
          }
          refreshInstalledPackages()
          manager.repositoryManager.refreshCashes()
        }
      }
    }
  }

  fun manageRepositories() {
    val updated = SingleConfigurableEditor(project, PyRepositoriesList(project)).showAndGet()
    if (updated) {
      PythonPackagesToolwindowStatisticsCollector.repositoriesChangedEvent.log(project)
      serviceScope.launch(Dispatchers.IO) {
        val packageService = PyPackageService.getInstance()
        val repositoryService = service<PyPackageRepositories>()
        val allRepos = repositoryService.repositories.map { it.repositoryUrl }
        packageService.additionalRepositories.asSequence()
          .filter { it !in allRepos }
          .forEach { packageService.removeRepository(it) }

        val (valid, invalid) = repositoryService.repositories.partition { it.checkValid() }
        repositoryService.invalidRepositories.clear()
        repositoryService.invalidRepositories.addAll(invalid)
        invalid.forEach { packageService.removeRepository(it.repositoryUrl!!) }

        valid.asSequence()
          .map { it.repositoryUrl }
          .filter { it !in packageService.additionalRepositories }
          .forEach { packageService.addRepository(it) }

        reloadPackages()
      }
    }
  }

  fun getMoreResultsForRepo(repository: PyPackageRepository, skipItems: Int): PyPackagesViewData {
    if (currentQuery.isNotEmpty()) {
      return sortPackagesForRepo(manager.repositoryManager.searchPackages(currentQuery, repository), currentQuery, repository, skipItems)
    }
    else {
      val packagesFromRepo = manager.repositoryManager.packagesFromRepository(repository)
      val page = packagesFromRepo.asSequence().limitResultAndFilterOutInstalled(repository, skipItems)
      return PyPackagesViewData(repository, page, moreItems = packagesFromRepo.size - (PACKAGES_LIMIT + skipItems))
    }
  }

  private fun Sequence<String>.limitResultAndFilterOutInstalled(repository: PyPackageRepository, skipItems: Int = 0): List<DisplayablePackage> {
    return drop(skipItems)
      .take(PACKAGES_LIMIT)
      .filter { pkg -> installedPackages.values.find { it.name.lowercase() == pkg.lowercase() } == null }
      .map { pkg -> InstallablePackage(pkg, repository) }
      .toList()
  }

  companion object {
    private const val PACKAGES_LIMIT = 50

    fun getInstance(project: Project) = project.service<PyPackagingToolWindowService>()

    private fun createNameComparator(query: String, url: String): Comparator<String> {
      val nameComparator = Comparator<String> { name1, name2 ->
        val queryLowerCase = query.lowercase()
        return@Comparator when {
          name1.startsWith(queryLowerCase) && name2.startsWith(queryLowerCase) -> name1.length - name2.length
          name1.startsWith(queryLowerCase) -> -1
          name2.startsWith(queryLowerCase) -> 1
          else -> name1.compareTo(name2)
        }
      }

      if (PyPIPackageUtil.isPyPIRepository(url)) {
        val ranking = service<PyPIPackageRanking>().packageRank
        return Comparator { p1, p2 ->
          val rank1 = ranking[p1.lowercase()]
          val rank2 = ranking[p2.lowercase()]
          return@Comparator when {
            rank1 != null && rank2 == null -> -1
            rank1 == null && rank2 != null -> 1
            rank1 != null && rank2 != null -> rank2 - rank1
            else -> nameComparator.compare(p1, p2)
          }
        }
      }

      return nameComparator
    }
  }
}