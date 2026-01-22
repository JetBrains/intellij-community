// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
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
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageService
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.management.*
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.PythonPackagesIdsHolder.Companion.PYTHON_PACKAGE_DELETED
import com.jetbrains.python.statistics.PythonPackagesIdsHolder.Companion.PYTHON_PACKAGE_INSTALLED
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
class PyPackagingToolWindowService(val project: Project, val serviceScope: CoroutineScope) : Disposable {
  private var toolWindowPanel: PyPackagingToolWindowPanel? = null
  private var installedPackages: Map<String, InstalledPackage> = emptyMap()
  private var searchJob: Job? = null
  private var currentQuery: String = ""

  private data class SdkContext(
    val sdk: Sdk,
    val managerUI: PythonPackageManagerUI
  ) {
    val manager: PythonPackageManager
      get() = managerUI.manager
  }

  private var sdkContext: SdkContext? = null

  internal val currentSdk: Sdk?
    get() = sdkContext?.sdk


  private val invalidRepositories: List<PyInvalidRepositoryViewData>
    get() = service<PyPackageRepositories>().invalidRepositories.map(::PyInvalidRepositoryViewData)

  fun initialize(toolWindowPanel: PyPackagingToolWindowPanel) {
    this.toolWindowPanel = toolWindowPanel
    serviceScope.launch(Dispatchers.IO) {
      initForSdk(project.modules.firstOrNull()?.pythonSdk)
    }
    subscribeToChanges()
  }

  suspend fun detailsForPackage(selectedPackage: DisplayablePackage): PythonPackageDetails? {
    val context = sdkContext ?: return null
    val packageManager = context.manager

    return withContext(Dispatchers.IO) {
      PythonPackagesToolwindowStatisticsCollector.requestDetailsEvent.log(project)
      val pkgName = PyPackageName.from(selectedPackage.name).name
      val pyRequirement = pyRequirement(pkgName)
      val repository = selectedPackage.repository

      val spec = if (repository != null)
        PythonRepositoryPackageSpecification(repository, pyRequirement)
      else
        packageManager.findPackageSpecification(pkgName) ?: return@withContext null
      packageManager.repositoryManager.getPackageDetails(spec.name, spec.repository).getOrNull()
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
    }
    else {
      StringUtil.containsIgnoreCase(PyPackageName.normalizePackageName(pkg.name), PyPackageName.normalizePackageName(query))
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
    currentQuery = query

    val context = sdkContext ?: return
    val packageManager = context.manager

    val prevSelected = toolWindowPanel?.getSelectedPackage()
    if (query.isNotEmpty()) {
      searchJob?.cancel()
      searchJob = serviceScope.launch {
        val allMatches = findAllMatchingPackages(query)
        val packagesFromRepos = packageManager.repositoryManager.searchPackages(query)
          .map { (repository, packages) -> sortPackagesForRepo(packages, query, repository) }
          .toList()

        if (isActive) {
          withContext(Dispatchers.EDT) {
            toolWindowPanel?.showSearchResult(allMatches, packagesFromRepos + invalidRepositories)
            prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
          }
        }
      }
    }
    else {
      val packagesByRepository = packageManager.repositoryManager.packagesByRepository().map { (repository, packages) ->
        val shownPackages = if (packages.size < PACKAGES_LIMIT) {
          packages.asSequence().limitResultAndFilterOutInstalled(repository)
        }
        else {
          emptyList()
        }

        val moreItems = if (packages.size < PACKAGES_LIMIT) {
          0
        }
        else {
          packages.size
        }
        PyPackagesViewData(repository, shownPackages, moreItems = moreItems)
      }.toList()

      toolWindowPanel?.resetSearch(installedPackages.values.toList(), packagesByRepository + invalidRepositories, currentSdk)
      prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
    }
  }

  suspend fun installPackage(installRequest: PythonPackageInstallRequest, options: List<String> = emptyList()) {
    val context = sdkContext ?: return
    val managerUI = context.managerUI

    withContext(TraceContext(message("tracecontext.packaging.tool.window.install"))) {
      PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
      managerUI.installPackagesRequestBackground(installRequest, options)?.let {
        handleActionCompleted(
          text = message("python.packaging.notification.installed", installRequest.title),
          displayId = PYTHON_PACKAGE_INSTALLED
        )
      }
      toolWindowPanel?.clearFocus()
    }
  }

  suspend fun installPackage(pkg: PythonPackage, options: List<String> = emptyList()) {
    val context = sdkContext ?: return
    withContext(TraceContext(message("tracecontext.packaging.tool.window.install"))) {
      val installRequest = context.manager.findPackageSpecification(pkg.name, pkg.version)?.toInstallRequest() ?: return@withContext
      PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
      context.managerUI.installPackagesRequestBackground(installRequest, options)?.let {
        handleActionCompleted(
          text = message("python.packaging.notification.installed", installRequest.title),
          displayId = PYTHON_PACKAGE_INSTALLED
        )
      }
      toolWindowPanel?.clearFocus()
    }
  }

  suspend fun deletePackage(vararg selectedPackages: InstalledPackage) {
    val context = sdkContext ?: return
    val managerUI = context.managerUI

    withContext(TraceContext(message("tracecontext.packaging.tool.window.delete"))) {
      PythonPackagesToolwindowStatisticsCollector.uninstallPackageEvent.log(project)
      managerUI.uninstallPackagesBackground(selectedPackages.map { it.instance.name }) ?: return@withContext
      handleActionCompleted(
        text = message("python.packaging.notification.deleted", selectedPackages.joinToString(", ") { it.name }),
        displayId = PYTHON_PACKAGE_DELETED
      )
      toolWindowPanel?.clearFocus()
    }
  }

  @ApiStatus.Internal
  suspend fun initForSdk(sdk: Sdk?) {
    if (sdk == currentSdk) {
      return
    }

    withContext(Dispatchers.EDT) {
      toolWindowPanel?.startLoadingSdk()
    }

    val previousSdk = currentSdk
    
    if (sdk == null) {
      sdkContext = null
      withContext(Dispatchers.EDT) {
        toolWindowPanel?.let {
          it.packageListController.setLoadingState(false)
          it.contentVisible = true
        }
      }
      showNoInterpreterMessage()
      return
    }

    sdkContext = SdkContext(
      sdk = sdk,
      managerUI = PythonPackageManagerUI.forSdk(project, sdk)
    )

    withContext(Dispatchers.EDT) {
      toolWindowPanel?.let {
        it.contentVisible = currentSdk != null
        if (currentSdk == null || currentSdk != previousSdk) {
          it.setEmpty()
        }
      }
    }

    withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
      refreshInstalledPackages()
    }
  }

  private fun showNoInterpreterMessage() {
    serviceScope.launch(Dispatchers.EDT) {
      installedPackages = emptyMap()
      toolWindowPanel?.let {
        it.packageListController.showNoSdkMessage()
        it.packageSelected(null)
      }
    }
  }

  private fun subscribeToChanges() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        if (sdkContext?.sdk == sdk) serviceScope.launch(Dispatchers.EDT + NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
          refreshInstalledPackages()
        }
      }

      override fun outdatedPackagesChanged(sdk: Sdk) {
        if (sdkContext?.sdk == sdk) serviceScope.launch(Dispatchers.EDT + NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
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
            }
            initForSdk(sdk)
          }
        }
      }
    })
  }

  suspend fun refreshInstalledPackages() {
    val context = sdkContext ?: return

    val declaredPackages = context.manager.extractDependencies()?.getOr {
      withContext(Dispatchers.EDT) {
        val errorMessage = context.manager.syncErrorMessage() ?: return@withContext
        showErrorNode(errorMessage.descriptionMessage, errorMessage.fixCommandMessage) {
          context.manager.sync()
        }
      }
      return
    } ?: emptyList()

    withContext(Dispatchers.Default) {
      val installedDeclaredPackages = findInstalledDeclaredPackages(context, declaredPackages)
      val treeExtractor = PythonPackageRequirementsTreeExtractor.forSdk(context.sdk)

      val packagesWithDependencies = if (treeExtractor != null) {
        processPackagesWithRequirementsTree(
          context,
          installedDeclaredPackages,
          treeExtractor,
        )
      }
      else {
        emptyList()
      }

      val standalonePackages = findStandalonePackages(context, packagesWithDependencies)
      installedPackages = (packagesWithDependencies + standalonePackages)
        .associateBy { it.name }
    }

    withContext(Dispatchers.EDT) {
      handleSearch(query = currentQuery)
    }
  }

  private suspend fun findInstalledDeclaredPackages(context: SdkContext, declaredPackages: List<PythonPackage>): List<PythonPackage> =
    context.manager.listInstalledPackages().filter {
      it.name in declaredPackages.map { pkg -> pkg.name }
    }

  private suspend fun processPackagesWithRequirementsTree(
    context: SdkContext,
    packages: List<PythonPackage>,
    treeExtractor: PythonPackageRequirementsTreeExtractor,
  ): List<InstalledPackage> {
    return packages.map { pkg ->
      val tree = treeExtractor.extract(pkg)
      createInstalledPackageFromTree(context, pkg, tree)
    }
  }

  private suspend fun createInstalledPackageFromTree(
    context: SdkContext,
    pkg: PythonPackage,
    tree: PackageNode,
  ): InstalledPackage {
    val spec = context.manager.findPackageSpecification(pkg.name, pkg.version)
    val repository = spec?.repository
    val nextVersionRaw = context.manager.listOutdatedPackagesSnapshot()[pkg.name]?.latestVersion
    val nextVersion = nextVersionRaw?.let { PyPackageVersionNormalizer.normalize(it) }
    val requirements = createRequirementsFromTree(context, tree.children, repository ?: PyPIPackageRepository)

    return InstalledPackage(pkg, repository, nextVersion, requirements)
  }

  private suspend fun createRequirementsFromTree(
    context: SdkContext,
    nodes: List<PackageNode>,
    repository: PyPackageRepository,
  ): List<RequirementPackage> {
    return nodes.mapNotNull { node ->
      val packageName = node.name.name
      val dependencyPkg = context.manager.listInstalledPackages().find { it.name == packageName }
      dependencyPkg?.let { depPkg ->
        val childRequirements = createRequirementsFromTree(context, node.children, repository)
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
    context: SdkContext,
    processedPackages: List<InstalledPackage>,
  ): List<InstalledPackage> {
    val processedPackageNames = processedPackages.flatMap { pkg ->
      collectPackageNamesRecursively(pkg)
    }.toSet()

    return context.manager.listInstalledPackages()
      .filter { it.name !in processedPackageNames }
      .map { pkg ->
        val repository = installedPackages.values.find { it.name == pkg.name }?.repository ?: PyPIPackageRepository
        val nextVersionRaw = context.manager.listOutdatedPackagesSnapshot()[pkg.name]?.latestVersion
        val nextVersion = nextVersionRaw?.let { PyPackageVersionNormalizer.normalize(it) }
        InstalledPackage(pkg, repository, nextVersion, emptyList())
      }
  }

  private suspend fun handleActionCompleted(text: @Nls String, displayId: String) {
    VirtualFileManager.getInstance().asyncRefresh()
    showPackagingNotification(text, displayId)
  }

  private suspend fun showPackagingNotification(text: @Nls String, displayId: String) {
    val notification = serviceAsync<NotificationGroupManager>()
      .getNotificationGroup("PythonPackages")
      .createNotification(text, NotificationType.INFORMATION)
      .setDisplayId(displayId)

    withContext(Dispatchers.EDT) {
      notification.notify(project)
    }
  }

  private fun sortPackagesForRepo(
    packageNames: List<String>,
    query: String,
    repository: PyPackageRepository,
    skipItems: Int = 0,
  ): PyPackagesViewData {

    val comparator = createNameComparator(query)

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
    val context = sdkContext
    if (context == null) {
      serviceScope.launch(Dispatchers.EDT) {
        toolWindowPanel?.packageListController?.setLoadingState(false)
      }
      showNoInterpreterMessage()
      return
    }
    
    serviceScope.launch(Dispatchers.Default) {
      context.managerUI.reloadPackagesBackground()
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

  fun getMoreResultsForRepo(repository: PyPackageRepository, skipItems: Int): PyPackagesViewData {
    val context = sdkContext ?: return PyPackagesViewData(repository, emptyList(), moreItems = 0)
    val packageManager = context.manager

    if (currentQuery.isNotEmpty()) {
      return sortPackagesForRepo(packageManager.repositoryManager.searchPackages(currentQuery, repository), currentQuery, repository, skipItems)
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
      .filter { pkg -> installedPackages.values.find { it.name.equals(pkg, ignoreCase = true) } == null }
      .map { pkg -> InstallablePackage(pkg, repository) }
      .toList()
  }

  fun showErrorNode(@Nls description: String, @Nls fixName: String, quickFixAction: (suspend () -> PyResult<*>)) {
    val quickFix = PackageQuickFix(fixName, quickFixAction)
    val errorNode = ErrorNode(description, quickFix)
    serviceScope.launch(Dispatchers.EDT) {
      installedPackages = emptyMap()
      handleSearch("")
      toolWindowPanel?.showErrorResult(errorNode)
    }
  }

  companion object {
    private const val PACKAGES_LIMIT = 50

    fun getInstance(project: Project): PyPackagingToolWindowService = project.service<PyPackagingToolWindowService>()

    private fun createNameComparator(query: String): Comparator<String> {
      val nameComparator = Comparator<String> { name1, name2 ->
        val queryLowerCase = query.lowercase()
        return@Comparator when {
          name1.startsWith(queryLowerCase) && name2.startsWith(queryLowerCase) -> name1.length - name2.length
          name1.startsWith(queryLowerCase) -> -1
          name2.startsWith(queryLowerCase) -> 1
          else -> name1.compareTo(name2)
        }
      }

      return nameComparator
    }
  }
}
