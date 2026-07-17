// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageService
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.notify
import com.jetbrains.python.packaging.packageRequirements.FlatPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PyRepositoriesList
import com.jetbrains.python.packaging.repository.checkValid
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.PyInvalidRepositoryViewData
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.sdk.PySdkListener
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.PythonPackagesIdsHolder.Companion.PYTHON_PACKAGE_DELETED
import com.jetbrains.python.statistics.PythonPackagesIdsHolder.Companion.PYTHON_PACKAGE_INSTALLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
internal class PyPackagingToolWindowService(val project: Project, val serviceScope: CoroutineScope) : Disposable {
  private var toolWindowPanel: PyPackagingToolWindowPanel? = null
  @Volatile private var installedPackages: List<DisplayablePackage> = emptyList()
  private var searchJob: Job? = null
  private var currentQuery: String = ""
  internal val activeSearchQuery: String get() = currentQuery

  private data class SdkContext(
    val sdk: Sdk,
    val managerUI: PythonPackageManagerUI
  ) {
    val manager: PythonPackageManager
      get() = managerUI.manager
  }

  @Volatile private var sdkContext: SdkContext? = null

  internal val currentSdk: Sdk?
    get() = sdkContext?.sdk


  private val invalidRepositories: List<PyInvalidRepositoryViewData>
    get() = service<PyPackageRepositories>().invalidRepositories.filter { it.enabled }.map(::PyInvalidRepositoryViewData)

  fun initialize(toolWindowPanel: PyPackagingToolWindowPanel) {
    this.toolWindowPanel = toolWindowPanel
    serviceScope.launch(Dispatchers.IO) {
      initForSdk(readAction { project.modules.firstNotNullOfOrNull { it.pythonSdk } })
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
      is InstallablePackage, is WorkspaceMember, is LoadingNode -> false
      is UndeclaredPackagesGroup, is DependencyGroupNode -> return false
    }

    return if (shouldUseStraightComparison) {
      StringUtil.containsIgnoreCase(pkg.name, query)
    }
    else {
      StringUtil.containsIgnoreCase(PyPackageName.normalizePackageName(pkg.name), PyPackageName.normalizePackageName(query))
    }
  }

  private fun isNonPipCondaPackage(pkg: PythonPackage): Boolean = pkg is CondaPackage && !pkg.installedWithPip

  /**
   * Finds all packages (both installed and requirements) that match the given query.
   */
  @ApiStatus.Internal
  fun findAllMatchingPackages(query: String): List<DisplayablePackage> = pruneTreeByQuery(installedPackages, query)

  private fun pruneTreeByQuery(packages: List<DisplayablePackage>, query: String): List<DisplayablePackage> {
    val result = mutableListOf<DisplayablePackage>()
    for (pkg in packages) {
      val prunedChildren = pruneTreeByQuery(pkg.getRequirements(), query)
      val selfMatches = nameMatches(pkg, query)
      val keep: DisplayablePackage? = when (pkg) {
        is WorkspaceMember -> if (prunedChildren.isNotEmpty()) WorkspaceMember(pkg.name, prunedChildren) else null
        is DependencyGroupNode -> if (prunedChildren.isNotEmpty()) DependencyGroupNode(pkg.name, prunedChildren) else null
        is UndeclaredPackagesGroup -> if (prunedChildren.isNotEmpty()) UndeclaredPackagesGroup(prunedChildren.filterIsInstance<InstalledPackage>()) else null
        is InstalledPackage -> if (selfMatches || prunedChildren.isNotEmpty()) InstalledPackage(
          pkg.instance, pkg.repository, pkg.nextVersion, prunedChildren.filterIsInstance<RequirementPackage>(), pkg.isDeclared, pkg.workspaceMember, pkg.dependencyGroup
        ) else null
        is RequirementPackage -> if (selfMatches || prunedChildren.isNotEmpty()) RequirementPackage(
          pkg.instance, pkg.repository, prunedChildren.filterIsInstance<RequirementPackage>(), pkg.group, pkg.isDeclared, pkg.workspaceMember
        ) else null
        is InstallablePackage -> if (selfMatches) pkg else null
        is LoadingNode -> null
      }
      if (keep != null) result.add(keep)
    }
    return result
  }

  fun rerunSearch() {
    handleSearch(currentQuery)
  }

  fun handleSearch(query: String) {
    currentQuery = query

    val context = sdkContext ?: return
    val packageManager = context.manager
    val prevSelected = toolWindowPanel?.getSelectedPackage()

    searchJob?.cancel()
    searchJob = serviceScope.launch {
      if (query.isNotEmpty()) {
        val allMatches = pruneTreeByQuery(installedPackages, query)
        var shouldRerun = false
        val packagesFromRepos =
          packageManager
            .repositoryManager
            .searchPackages(query)
            .mapNotNull { (repository, result) ->
              if (result.pages.isEmpty()) return@mapNotNull null
              val allNames = mutableListOf<String>()
              var pageInvalidated = false
              // Drains every page already produced by the cache.search call for this typed
              // query. Bounded by the per-query match count (PyPI typed query ≈ hundreds, not
              // 800k). Eager flatten + single global sort gives the same priority order the
              // install dialog shows, paginated visually via tree.loadMore.
              for (page in result.pages) {
                val contents = page.contents().successOrNull
                if (contents == null) {
                  pageInvalidated = true
                  break
                }
                allNames.addAll(contents)
              }
              if (pageInvalidated) {
                shouldRerun = true
                return@mapNotNull null
              }
              val comparator = createNameComparator(query)
              val sortedAll = allNames.asSequence()
                .filterOutInstalled(repository)
                .sortedWith(compareBy(comparator) { it.name })
                .toList()
              val displayable = sortedAll.take(PACKAGES_LIMIT)
              val exactMatch = displayable.indexOfFirst { StringUtil.equalsIgnoreCase(it.name, query) }
              PyPackagesViewData(repository, result, 0, displayable, exactMatch, sortedAll)
            }
            .toList()

        if (shouldRerun) {
          rerunSearch()
          return@launch
        }

        if (isActive) {
          withContext(Dispatchers.EDT) {
            toolWindowPanel?.showSearchResult(allMatches, packagesFromRepos + invalidRepositories)
            prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
          }
        }
      }
      else {
        var shouldRerun = false
        val packagesByRepository =
          packageManager
            .repositoryManager
            .searchPackages("", PACKAGES_LIMIT)
            .mapNotNull { (repository, result) ->
              val displayable =
                if (result.pages.size == 1) {
                  result.pages[0].contents().successOrNull?.asSequence()?.filterOutInstalled(repository)
                }
                else {
                  emptyList()
                }

              if (displayable == null) {
                shouldRerun = true
                return@mapNotNull null
              }

              PyPackagesViewData(repository, result, 0, displayable)
            }
            .toList()

        if (shouldRerun) {
          rerunSearch()
          return@launch
        }

        if (isActive) {
          withContext(Dispatchers.EDT) {
            toolWindowPanel?.resetSearch(installedPackages, currentSdk)
            prevSelected?.name?.let { toolWindowPanel?.selectPackageName(it) }
          }
        }
      }
    }
  }

  suspend fun installPackage(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
    workspaceMember: PyWorkspaceMember? = null,
    dependencyGroup: PyDependencyGroup? = null,
  ) {
    val context = sdkContext ?: return
    val managerUI = context.managerUI
    val module = workspaceMember?.let { context.manager.workspaceSupport?.resolveModule(it) }

    withContext(TraceContext(message("trace.context.packaging.tool.window.install"))) {
      PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
      managerUI.installPackagesRequestBackground(installRequest, options, module, dependencyGroup)?.let {
        handleActionCompleted(
          text = message("python.packaging.notification.installed", installRequest.title),
          displayId = PYTHON_PACKAGE_INSTALLED
        )
      }
      notifyPackageActionCompleted()
    }
  }

  suspend fun installPackage(pkg: PythonPackage, options: List<String> = emptyList()) {
    val context = sdkContext ?: return
    withContext(TraceContext(message("trace.context.packaging.tool.window.install"))) {
      val installRequest = context.manager.findPackageSpecification(pkg.name, pkg.version)?.toInstallRequest() ?: return@withContext
      PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
      context.managerUI.installPackagesRequestBackground(installRequest, options)?.let {
        handleActionCompleted(
          text = message("python.packaging.notification.installed", installRequest.title),
          displayId = PYTHON_PACKAGE_INSTALLED
        )
      }
      notifyPackageActionCompleted()
    }
  }

  suspend fun deletePackage(vararg selectedPackages: InstalledPackage) {
    val context = sdkContext ?: return
    val managerUI = context.managerUI

    withContext(TraceContext(message("trace.context.packaging.tool.window.delete"))) {
      PythonPackagesToolwindowStatisticsCollector.uninstallPackageEvent.log(project)

      val packagesByKey = selectedPackages.groupBy { Pair(it.workspaceMember, it.dependencyGroup) }

      for ((key, packages) in packagesByKey) {
        val (workspaceMember, dependencyGroup) = key
        val packageNames = packages.map { it.instance.name }
        managerUI.uninstallPackagesBackground(packageNames, workspaceMember, dependencyGroup) ?: return@withContext
      }

      refreshInstalledPackages()
      handleActionCompleted(
        text = message("python.packaging.notification.deleted", selectedPackages.joinToString(", ") { it.name }),
        displayId = PYTHON_PACKAGE_DELETED
      )
      notifyPackageActionCompleted()
    }
  }

  /**
   * Signals to the tool-window view that a package install / uninstall has finished, so the
   * view can reset its own UI state (search text, focus owner, …). Service side just knows
   * "the action completed" and does not touch Swing directly — the view decides what "completed"
   * means visually.
   */
  private suspend fun notifyPackageActionCompleted() {
    withContext(Dispatchers.EDT) {
      toolWindowPanel?.onPackageActionCompleted()
    }
  }

  @ApiStatus.Internal
  suspend fun initForSdk(sdk: Sdk?) {
    if (project.isDisposed) return
    if (sdk != null && sdk == currentSdk) {
      return
    }

    val previousSdk = currentSdk

    if (sdk == null) {
      sdkContext = null
      withContext(Dispatchers.EDT) {
        toolWindowPanel?.let {
          it.packageListController.setLoadingState(false)
          it.contentVisible = false
        }
      }
      showNoInterpreterMessage()
      return
    }

    withContext(Dispatchers.EDT) {
      toolWindowPanel?.let {
        it.startLoadingSdk(sdk.name)
        it.syncSdkControllerSelection(sdk)
      }
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
      installedPackages = emptyList()
      toolWindowPanel?.packageListController?.showNoSdkMessage()
    }
  }

  private fun subscribeToChanges() {
    subscribeToSdkChanges()
    subscribeToPackageManagementChanges()
    subscribeToProjectChanges()
  }

  private fun subscribeToSdkChanges() {
    ApplicationManager.getApplication().messageBus.connect(serviceScope)
      .subscribe(PySdkListener.TOPIC, object : PySdkListener {
        override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
          if (newSdk != null && newSdk == currentSdk) return
          serviceScope.launch(Dispatchers.IO) {
            initForSdk(newSdk)
          }
        }
      })
  }

  private fun subscribeToPackageManagementChanges() {
    ApplicationManager.getApplication().messageBus.connect(serviceScope)
      .subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
        override fun packagesChanged(sdk: Sdk) {
          val context = sdkContext ?: return
          if (context.sdk == sdk) {
            serviceScope.launch(Dispatchers.IO + NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
              refreshInstalledPackages()
            }
          }
        }

        override fun outdatedPackagesChanged(sdk: Sdk) {
          val context = sdkContext ?: return
          if (context.sdk == sdk) {
            serviceScope.launch(Dispatchers.IO + NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
              refreshInstalledPackages(showIndicator = false)
            }
          }
        }
      })
  }

  private fun subscribeToProjectChanges() {
    val connection = project.messageBus.connect(this)
    subscribeToRootChanges(connection)
    subscribeToFileEditorChanges(connection)
  }

  // Needed alongside PySdkListener: covers structural changes (module added/removed) that affect SDK availability
  private fun subscribeToRootChanges(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        serviceScope.launch(Dispatchers.IO) {
          val current = currentSdk
          val allSdks = readAction { project.modules.mapNotNull { it.pythonSdk } }
          if (current != null && current in allSdks) return@launch
          val sdk = allSdks.firstOrNull()
          if (sdk != current) {
            initForSdk(sdk)
          }
        }
      }
    })
  }

  private fun subscribeToFileEditorChanges(connection: MessageBusConnection) {
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

  suspend fun refreshInstalledPackages(showIndicator: Boolean = true) {
    if (project.isDisposed) return
    val context = sdkContext ?: return
    
    if (showIndicator) {
      showRefreshIndicatorIfNeeded()
    }
    
    try {
      refreshInstalledPackagesImpl(context)
    }
    finally {
      if (showIndicator) {
        hideRefreshIndicatorIfNeeded()
      }
    }
  }

  private suspend fun showRefreshIndicatorIfNeeded() {
      withContext(Dispatchers.EDT) {
        toolWindowPanel?.setRefreshIndicatorVisible(true)
      }
  }

  private suspend fun hideRefreshIndicatorIfNeeded() {
      withContext(Dispatchers.EDT) {
        toolWindowPanel?.setRefreshIndicatorVisible(false)
    }
  }

  private suspend fun refreshInstalledPackagesImpl(context: SdkContext) {
    val packageIndex = PackageIndex(context.manager)
    val packageTree = context.manager.getPackageTree()

    val declaredPackageNames = collectDeclaredNames(packageTree, packageIndex)

    withContext(Dispatchers.Default) {
      val allPackages = buildPackages(context, packageTree, packageIndex, declaredPackageNames)
      installedPackages = allPackages.sortedWith(compareBy({ getSortPriority(it) }, { it.name.lowercase() }))
    }

    withContext(Dispatchers.EDT) {
      handleSearch(query = currentQuery)
    }
  }

  private class PackageIndex(
    val installedByName: Map<String, PythonPackage>,
    val outdated: Map<String, PythonOutdatedPackage>,
  ) {
    companion object {
      suspend operator fun invoke(manager: PythonPackageManager): PackageIndex {
        return PackageIndex(
          installedByName = manager.listInstalledPackages().associateBy { it.name },
          outdated = manager.listOutdatedPackages(),
        )
      }
    }
  }

  private suspend fun buildPackages(
    context: SdkContext,
    node: PackageStructureNode,
    packageIndex: PackageIndex,
    declaredPackageNames: Set<String>,
  ): List<DisplayablePackage> {
    return when (node) {
      is WorkspaceMemberPackageStructureNode -> {
        val workspaceMembers = buildWorkspaceMembers(context, node, packageIndex, declaredPackageNames)
        val undeclared = buildInstalledPackages(context, node.undeclaredPackages, packageIndex, declaredPackageNames)
        val result = mutableListOf<DisplayablePackage>()
        if (undeclared.isNotEmpty()) {
          result.add(UndeclaredPackagesGroup(undeclared))
        }
        result.addAll(workspaceMembers)
        result
      }
      is PackageCollectionPackageStructureNode -> {
        val declared = buildInstalledPackages(context, node.declaredPackages, packageIndex, declaredPackageNames)
        val undeclared = buildInstalledPackages(context, node.undeclaredPackages, packageIndex, declaredPackageNames)
        val result = mutableListOf<DisplayablePackage>()
        if (undeclared.isNotEmpty()) {
          result.add(UndeclaredPackagesGroup(undeclared))
        }
        result.addAll(declared)
        result
      }
      is FlatPackageStructureNode -> {
        buildPackagesFromManager(packageIndex, declaredPackageNames)
      }
    }
  }

  private fun buildPackagesFromManager(
    packageIndex: PackageIndex,
    declaredPackageNames: Set<String>,
  ): List<InstalledPackage> {
    return packageIndex.installedByName.values.map { pkg ->
      val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
      InstalledPackage(pkg, defaultRepositoryFor(pkg), nextVersion, emptyList(), isDeclared = pkg.name in declaredPackageNames)
    }
  }

  private fun collectDeclaredNames(node: PackageStructureNode, packageIndex: PackageIndex): Set<String> {
    val names = mutableSetOf<String>()
    when (node) {
      is PackageCollectionPackageStructureNode -> {
        node.declaredPackages.forEach { names.addAll(it.collectAllNames()) }
      }
      is WorkspaceMemberPackageStructureNode -> {
        node.packageTree?.let { names.addAll(it.collectAllNames()) }
        node.subMembers.forEach { member ->
          member.packageTree?.let { names.addAll(it.collectAllNames()) }
        }
      }
      is FlatPackageStructureNode -> return packageIndex.installedByName.keys
    }
    return names
  }

  private fun getSortPriority(pkg: DisplayablePackage): Int {
    return when (pkg) {
      is UndeclaredPackagesGroup -> 0  // undeclared group always first
      is WorkspaceMember -> 1
      is DependencyGroupNode -> 1
      is InstalledPackage -> if (pkg.isDeclared) 1 else 2
      is RequirementPackage -> if (pkg.isDeclared) 1 else 2
      is InstallablePackage, is LoadingNode -> 3
    }
  }

  private suspend fun buildWorkspaceMembers(
    context: SdkContext,
    root: WorkspaceMemberPackageStructureNode,
    packageIndex: PackageIndex,
    declaredPackageNames: Set<String>,
  ): List<WorkspaceMember> {
    val members = mutableListOf<WorkspaceMemberPackageStructureNode>()
    root.packageTree?.let { members.add(root) }

    for (subMember in root.subMembers) {
      if (subMember.packageTree != null) members.add(subMember)
    }

    return members.mapNotNull { member ->
      member.packageTree?.let { packageTree ->
        buildWorkspaceMember(context, member.name, packageTree, packageIndex, declaredPackageNames)
      }
    }
  }

  private suspend fun buildWorkspaceMember(
    context: SdkContext,
    memberName: String,
    tree: PackageTreeNode,
    packageIndex: PackageIndex,
    declaredPackageNames: Set<String>,
  ): WorkspaceMember {
    val member = PyWorkspaceMember(memberName)
    val packages = tree.children.distinctBy { it.name.name }.mapNotNull { node ->
      val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
      val repository = resolveRepository(context, pkg)
      val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
      val requirements = buildRequirements(node.children, packageIndex, repository, true, member, declaredPackageNames)
      InstalledPackage(pkg, repository, nextVersion, requirements, isDeclared = true, workspaceMember = member, dependencyGroup = node.group?.let { PyDependencyGroup(it) })
    }
    return WorkspaceMember(memberName, packages)
  }

  private suspend fun buildInstalledPackages(
    context: SdkContext,
    nodes: List<PackageTreeNode>,
    packageIndex: PackageIndex,
    declaredPackageNames: Set<String>,
    workspaceMember: PyWorkspaceMember? = null,
  ): List<InstalledPackage> {
    return nodes.mapNotNull { node ->
      val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
      val repository = resolveRepository(context, pkg)
      val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
      val isDeclared = pkg.name in declaredPackageNames
      val requirements = buildRequirements(node.children, packageIndex, repository, isDeclared, workspaceMember, declaredPackageNames)
      InstalledPackage(pkg, repository, nextVersion, requirements, isDeclared, workspaceMember, dependencyGroup = node.group?.let { PyDependencyGroup(it) })
    }
  }

  private suspend fun resolveRepository(context: SdkContext, pkg: PythonPackage): PyPackageRepository {
    if (pkg is CondaPackage && !pkg.installedWithPip) return CondaPackageRepository
    return context.manager.findPackageSpecification(pkg.name, pkg.version)?.repository ?: defaultRepositoryFor(pkg)
  }

  private fun defaultRepositoryFor(pkg: PythonPackage): PyPackageRepository =
    if (pkg is CondaPackage && !pkg.installedWithPip) CondaPackageRepository else PyPiPackageRepository

  private fun buildRequirements(
    nodes: List<PackageTreeNode>,
    packageIndex: PackageIndex,
    repository: PyPackageRepository,
    isDeclared: Boolean,
    workspaceMember: PyWorkspaceMember?,
    declaredPackageNames: Set<String>,
  ): List<RequirementPackage> {
    return nodes.mapNotNull { node ->
      val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
      val effectiveIsDeclared = pkg.name in declaredPackageNames || isDeclared
      val childRequirements = buildRequirements(node.children, packageIndex, repository, effectiveIsDeclared, workspaceMember, declaredPackageNames)
      RequirementPackage(pkg, repository, childRequirements, node.group, effectiveIsDeclared, workspaceMember)
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

  private fun processPackagesForRepo(
    result: PythonPackageSearchResult,
    pageIndex: Int,
    query: String,
    repository: PyPackageRepository,
  ): Result<PyPackagesViewData, PythonPackageSearchPage.DataInvalidatedError> {
    val contents = result.pages[pageIndex].contents().getOr { return it }
    val shownPackages = contents.asSequence().filterOutInstalled(repository)
    val exactMatch = shownPackages.indexOfFirst { StringUtil.equalsIgnoreCase(it.name, query) }
    return Result.Success(PyPackagesViewData(repository, result, pageIndex, shownPackages, exactMatch))
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
    serviceScope.launch(Dispatchers.Default + TraceContext(message("trace.context.packaging.tool.window"), serviceScope)) {
      withContext(TraceContext(message("trace.context.packaging.tool.window.sdk.reload", context.sdk.name))) {
        context.managerUI.reloadPackagesBackground()
        refreshInstalledPackages()
      }
    }
  }

  fun manageRepositories() {
    val updated = SingleConfigurableEditor(project, PyRepositoriesList(project)).showAndGet()
    if (updated) {
      PythonPackagesToolwindowStatisticsCollector.repositoriesChangedEvent.log(project)
      serviceScope.launch(Dispatchers.IO + TraceContext(message("trace.context.packaging.tool.window"), serviceScope)) {
        val packageService = PyPackageService.getInstance()
        val repositoryService = service<PyPackageRepositories>()
        val allRepos = repositoryService.repositories.map { it.repositoryUrl }
        packageService.additionalRepositories.asSequence()
          .filter { it !in allRepos }
          .forEach { packageService.removeRepository(it) }

        val (valid, invalid) = repositoryService.repositories.partition { it.checkValid() }
        repositoryService.invalidRepositories.clear()
        repositoryService.invalidRepositories.addAll(invalid)
        invalid.forEach { repo ->
          if (repo.repositoryUrl.isNotEmpty()) packageService.removeRepository(repo.repositoryUrl)
          repo.enabled = false
        }

        valid.asSequence()
          .map { it.repositoryUrl }
          .filter { it !in packageService.additionalRepositories }
          .forEach { packageService.addRepository(it) }

        project.service<PipRepositoryManager>()
          .refreshAddedCaches()
          .onFailure {
            it.notify(project)
          }
        
        refreshInstalledPackages()
      }
    }
  }

  fun getMoreResultsForPage(
    repository: PyPackageRepository,
    result: PythonPackageSearchResult,
    pageIndex: Int,
  ): Result<PyPackagesViewData, PythonPackageSearchPage.DataInvalidatedError> {
    return processPackagesForRepo(
      result,
      pageIndex + 1,
      currentQuery,
      repository
    )
  }

  /**
   * Wraps every repository hit in [InstallablePackage]. Historically this filter also stripped
   * items whose names matched the currently installed set (so an "installed" package would never
   * appear in the "Uninstalled" tree). The filter hid rows that repositories still counted toward
   * the header total — the group header could show `Conda (1 found)` with no rows underneath.
   * The user-visible fix is to keep every row the repository returned; the "already installed"
   * status is now surfaced by the row rendering, not by hiding the row.
   */
  private fun Sequence<String>.filterOutInstalled(repository: PyPackageRepository): List<DisplayablePackage> {
    return map { pkg -> InstallablePackage(pkg, repository) }.toList()
  }


  companion object {
    private const val PACKAGES_LIMIT = 50

    fun getInstance(project: Project): PyPackagingToolWindowService = project.service<PyPackagingToolWindowService>()

    /**
     * Query-aware comparator over package names: prefix matches first (shortest wins), then plain
     * lexicographic name fallback so the sort is stable when two items tie on the primary key.
     */
    internal fun createNameComparator(query: String): Comparator<String> {
      val queryLowerCase = query.lowercase()
      return Comparator<String> { name1, name2 ->
        when {
          name1.startsWith(queryLowerCase) && name2.startsWith(queryLowerCase) -> name1.length - name2.length
          name1.startsWith(queryLowerCase) -> -1
          name2.startsWith(queryLowerCase) -> 1
          else -> 0
        }
      }.thenBy { it }
    }
  }
}
