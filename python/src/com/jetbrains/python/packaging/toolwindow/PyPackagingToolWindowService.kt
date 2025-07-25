// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.management.*
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
class PyPackagingToolWindowService(val project: Project, val serviceScope: CoroutineScope) : Disposable {
  private var toolWindowPanel: PyPackagingToolWindowPanel? = null
  private var installedPackages: Map<String, InstalledPackage> = emptyMap()
  private var searchJob: Job? = null
  private var currentQuery: String = ""

  internal var currentSdk: Sdk? = null
  private lateinit var managerUI: PythonPackageManagerUI

  private val manager: PythonPackageManager
    get() = managerUI.manager


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

  suspend fun detailsForPackage(selectedPackage: DisplayablePackage): PythonPackageDetails? {
    val packageManager = manager ?: return null
    return withContext(Dispatchers.IO) {
      PythonPackagesToolwindowStatisticsCollector.requestDetailsEvent.log(project)
      val pkgName = NormalizedPythonPackageName.from(selectedPackage.name).name
      val spec = when (selectedPackage) {
        is InstalledPackage -> packageManager.findPackageSpecification(pkgName)
        is InstallablePackage -> selectedPackage.repository.findPackageSpecification(pkgName)
        is ExpandResultNode -> selectedPackage.repository.findPackageSpecification(pkgName)
        is RequirementPackage -> selectedPackage.repository.findPackageSpecification(pkgName)
      }

      if (spec == null) {
        return@withContext null
      }

      spec.let { packageManager.repositoryManager.getPackageDetails(it).getOrThrow() }
    }
  }

  private fun nameMatches(pkg: DisplayablePackage, query: String): Boolean {
    val shouldUseStraightComparison = when (pkg) {
      is InstalledPackage -> isNonPipCondaPackage(pkg.instance)
      is RequirementPackage -> isNonPipCondaPackage(pkg.instance)
      else -> false
    }

    return if (shouldUseStraightComparison) {
      StringUtil.containsIgnoreCase(pkg.name, query)
    } else {
      StringUtil.containsIgnoreCase(normalizePackageName(pkg.name), normalizePackageName(query))
    }
  }

  private fun isNonPipCondaPackage(pkg: PythonPackage): Boolean = pkg is CondaPackage && !pkg.installedWithPip

  private fun traversePackageTree(
    pkg: DisplayablePackage,
    visited: MutableSet<String>,
    matches: MutableList<RequirementPackage>,
    query: String,
  ) {
    if (!visited.add(pkg.name)) return

    if (pkg is RequirementPackage && nameMatches(pkg, query)) {
      matches.add(pkg)
    }

    for (requirementPackage in pkg.getRequirements()) {
      traversePackageTree(requirementPackage, visited, matches, query)
    }
  }

  /**
   * Finds all packages (both installed and requirements) that match the given query.
   */
  @ApiStatus.Internal
  fun findAllMatchingPackages(query: String): List<DisplayablePackage> {
    val matchingInstalled = installedPackages.values.filter { nameMatches(it, query) }
    val matchingRequirements = mutableListOf<RequirementPackage>()
    val visited = mutableSetOf<String>()

    for (pkg in installedPackages.values) {
      traversePackageTree(pkg, visited, matchingRequirements, query)
    }

    return unifyPackages(matchingInstalled, matchingRequirements)
  }

  /**
   * Unifies packages with the same name according to the following rules:
   * 1. If both an installed package and a requirement package have the same name, keep only the installed package.
   * 2. If multiple requirement packages have the same name, keep only one of them.
   */
  private fun unifyPackages(installedPackages: List<InstalledPackage>, requirementPackages: List<RequirementPackage>): List<DisplayablePackage> {
    return (installedPackages + requirementPackages)
      .groupBy { it.name.lowercase() }
      .map { (_, packages) ->
        packages.find { it is InstalledPackage } ?: packages.first()
      }
  }

  fun handleSearch(query: String) {
    val manager = manager ?: return
    val prevSelected = toolWindowPanel?.getSelectedPackage()

    currentQuery = query
    if (query.isNotEmpty()) {
      searchJob?.cancel()
      searchJob = serviceScope.launch {
        val allMatches = findAllMatchingPackages(query)
        val packagesFromRepos = manager.repositoryManager.searchPackages(query)
          .map { (repository, packages) -> sortPackagesForRepo(packages, query, repository) }
          .toList()

        if (isActive) {
          withContext(Dispatchers.Main) {
            toolWindowPanel?.showSearchResult(allMatches, packagesFromRepos + invalidRepositories)
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

  suspend fun installPackage(installRequest: PythonPackageInstallRequest, options: List<String> = emptyList()) {
    PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
    managerUI.installPackagesRequestBackground(installRequest, options)?.let {
      handleActionCompleted(message("python.packaging.notification.installed", installRequest.title))
    }
  }

  suspend fun installPackage(pkg: PythonPackage, options: List<String> = emptyList()) {
    val installRequest = manager?.findPackageSpecification(pkg.name, pkg.version)?.toInstallRequest() ?: return
    PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
    managerUI.installPackagesRequestBackground(installRequest, options)?.let {
      handleActionCompleted(message("python.packaging.notification.installed", installRequest.title))
    }
  }

  suspend fun deletePackage(vararg selectedPackages: InstalledPackage) {
    PythonPackagesToolwindowStatisticsCollector.uninstallPackageEvent.log(project)
    managerUI.uninstallPackagesBackground(selectedPackages.map { it.instance.name }) ?: return
    handleActionCompleted(message("python.packaging.notification.deleted", selectedPackages.joinToString(", ") { it.name }))
  }

  @ApiStatus.Internal
  suspend fun initForSdk(sdk: Sdk?) {
    if (sdk == null) {
      toolWindowPanel?.packageListController?.setLoadingState(false)
    }

    if (sdk == currentSdk) {
      return
    }

    withContext(Dispatchers.EDT) {
      toolWindowPanel?.startLoadingSdk()
    }

    val previousSdk = currentSdk
    currentSdk = sdk
    if (sdk == null) {
      return
    }
    managerUI = PythonPackageManagerUI.forSdk(project, sdk)


    withContext(Dispatchers.EDT) {
      toolWindowPanel?.contentVisible = currentSdk != null
      if (currentSdk == null || currentSdk != previousSdk) {
        toolWindowPanel?.setEmpty()
      }
    }
    refreshInstalledPackages()
  }

  private fun subscribeToChanges() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        if (currentSdk == sdk) serviceScope.launch(Dispatchers.Main) {
          refreshInstalledPackages()
        }
      }

      override fun outdatedPackagesChanged(sdk: Sdk) {
        if (currentSdk == sdk) serviceScope.launch(Dispatchers.Main) {
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
    val sdk = currentSdk ?: return
    val manager = manager ?: return
    withContext(Dispatchers.Default) {
      val declaredPackages = manager.reloadDependencies()
      val installedDeclaredPackages = findInstalledDeclaredPackages(declaredPackages)
      val treeExtractor = PythonPackageRequirementsTreeExtractor.forSdk(sdk)

      val packagesWithDependencies = if (treeExtractor != null) {
        processPackagesWithRequirementsTree(
          installedDeclaredPackages,
          treeExtractor,
        )
      }
      else {
        emptyList()
      }

      val standalonePackages = findStandalonePackages(packagesWithDependencies)

      installedPackages = (packagesWithDependencies + standalonePackages)
        .associateBy { it.name }
    }

    withContext(Dispatchers.Main) {
      handleSearch(query = currentQuery)
    }
  }

  private suspend fun findInstalledDeclaredPackages(declaredPackages: List<PythonPackage>): List<PythonPackage> =
    manager?.listInstalledPackages()?.filter {
      it.name in declaredPackages.map { pkg -> pkg.name }
    } ?: emptyList()

  private suspend fun processPackagesWithRequirementsTree(
    packages: List<PythonPackage>,
    treeExtractor: PythonPackageRequirementsTreeExtractor,
  ): List<InstalledPackage> {
    return packages.mapNotNull { pkg ->
      val tree = treeExtractor.extract(pkg)
      createInstalledPackageFromTree(pkg, tree)
    }
  }

  private suspend fun createInstalledPackageFromTree(
    pkg: PythonPackage,
    tree: PackageNode,
  ): InstalledPackage? {
    val manager = manager ?: return null
    val spec = manager.findPackageSpecification(pkg.name, pkg.version)
    val repository = spec?.repository
    val nextVersionRaw = manager.listOutdatedPackagesSnapshot()[pkg.name]?.latestVersion
    val nextVersion = nextVersionRaw?.let { PyPackageVersionNormalizer.normalize(it) }
    val requirements = createRequirementsFromTree(tree.children, repository ?: PyPIPackageRepository)

    return InstalledPackage(pkg, repository, nextVersion, requirements)
  }

  private suspend fun createRequirementsFromTree(
    nodes: List<PackageNode>,
    repository: PyPackageRepository,
  ): List<RequirementPackage> {
    val manager = manager ?: return emptyList()

    return nodes.mapNotNull { node ->
      val packageName = node.name.name
      val dependencyPkg = manager.listInstalledPackages().find { it.name == packageName }
      dependencyPkg?.let { depPkg ->
        val childRequirements = createRequirementsFromTree(node.children, repository)
        RequirementPackage(depPkg, repository, childRequirements)
      }
    }
  }

  private fun collectPackageNamesRecursively(
    pkg: DisplayablePackage,
  ): List<String> {
    return listOf(pkg.name) + pkg.getRequirements().flatMap { requirement ->
      collectPackageNamesRecursively(requirement)
    }
  }

  private suspend fun findStandalonePackages(
    processedPackages: List<InstalledPackage>,
  ): List<InstalledPackage> {
    val manager = manager ?: return emptyList()
    val processedPackageNames = processedPackages.flatMap { pkg ->
      collectPackageNamesRecursively(pkg)
    }.toSet()

    return manager.listInstalledPackages()
      .filter { it.name !in processedPackageNames }
      .map { pkg ->
        val repository = installedPackages.values.find { it.name == pkg.name }?.repository ?: PyPIPackageRepository
        val nextVersionRaw = manager.listOutdatedPackagesSnapshot()[pkg.name]?.latestVersion
        val nextVersion = nextVersionRaw?.let { PyPackageVersionNormalizer.normalize(it) }
        InstalledPackage(pkg, repository, nextVersion, emptyList())
      }
  }

  private suspend fun handleActionCompleted(text: @Nls String) {
    VirtualFileManager.getInstance().asyncRefresh()
    showPackagingNotification(text)
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
    val moreItems = (packageNames.size - (skipItems + PACKAGES_LIMIT)).takeIf { it > 0 } ?: 0
    return PyPackagesViewData(repository, shownPackages, exactMatch, moreItems)
  }

  override fun dispose() {
    searchJob?.cancel()
    serviceScope.cancel()
  }

  fun reloadPackages() {
    serviceScope.launch(Dispatchers.Default) {
      managerUI.reloadPackagesBackground()
      refreshInstalledPackages()
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

        // LAME: pip based repository manager handles all added repositories via cache...
        service<PythonSimpleRepositoryCache>().refresh()
        refreshInstalledPackages()
      }
    }
  }

  fun getMoreResultsForRepo(repository: PyPackageRepository, skipItems: Int): PyPackagesViewData? {
    val manager = manager ?: return null
    if (currentQuery.isNotEmpty()) {
      return sortPackagesForRepo(manager.repositoryManager.searchPackages(currentQuery, repository), currentQuery, repository, skipItems)
    }
    else {
      val packagesFromRepo = repository.getPackages()
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

    fun getInstance(project: Project): PyPackagingToolWindowService = project.service<PyPackagingToolWindowService>()

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
