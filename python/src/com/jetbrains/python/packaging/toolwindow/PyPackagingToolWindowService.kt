// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.ProjectTopics
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import com.jetbrains.python.PyBundle.*
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.packagesByRepository
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.applyHelperPackageToPythonPath
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.sdkFlavor
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.*
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.jetbrains.annotations.Nls

@Service
class PyPackagingToolWindowService(val project: Project) : Disposable {

  private var toolWindowPanel: PyPackagingToolWindowPanel? = null
  lateinit var manager: PythonPackageManager
  private var installedPackages: List<InstalledPackage> = emptyList()
  internal var currentSdk: Sdk? = null
  private var searchJob: Job? = null
  private var currentQuery: String = ""
  private val serviceScope = ApplicationManager.getApplication().coroutineScope.childScope(Dispatchers.Default)

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

  suspend fun detailsForPackage(selectedPackage: DisplayablePackage): PythonPackageDetails {
    PythonPackagesToolwindowStatisticsCollector.requestDetailsEvent.log(project)
    val spec = selectedPackage.repository.createPackageSpecification(selectedPackage.name)
    return manager.repositoryManager.getPackageDetails(spec)
  }


  fun handleSearch(query: String) {
    currentQuery = query
    if (query.isNotEmpty()) {
      searchJob?.cancel()
      searchJob = serviceScope.launch {
        val installed = installedPackages.filter { StringUtil.containsIgnoreCase(it.name, query) }

        val packagesFromRepos = manager.repositoryManager.packagesByRepository().map {
          filterPackagesForRepo(it.second, query, it.first)
        }.toList()

        if (isActive) {
          withContext(Dispatchers.Main) {
            toolWindowPanel?.showSearchResult(installed, packagesFromRepos + invalidRepositories)
          }
        }
      }
    }
    else {
      val packagesByRepository = manager.repositoryManager.packagesByRepository().map { (repository, packages) ->
        val shownPackages = packages.asSequence().limitDisplayableResult(repository)
        PyPackagesViewData(repository, shownPackages, moreItems = packages.size - PACKAGES_LIMIT)
      }.toList()

      toolWindowPanel?.resetSearch(installedPackages, packagesByRepository + invalidRepositories)
    }
  }

  suspend fun installPackage(specification: PythonPackageSpecification) {
    PythonPackagesToolwindowStatisticsCollector.installPackageEvent.log(project)
    val result = manager.installPackage(specification)
    if (result.isSuccess) showPackagingNotification(message("python.packaging.notification.installed", specification.name))
  }

  suspend fun deletePackage(selectedPackage: InstalledPackage) {
    PythonPackagesToolwindowStatisticsCollector.uninstallPackageEvent.log(project)
    val result = manager.uninstallPackage(selectedPackage.instance)
    if (result.isSuccess) showPackagingNotification(message("python.packaging.notification.deleted", selectedPackage.name))
  }

  internal suspend fun initForSdk(sdk: Sdk?) {
    val previousSdk = currentSdk
    currentSdk = sdk
    if (currentSdk != null) {
      manager = PythonPackageManager.forSdk(project, currentSdk!!)
      manager.repositoryManager.initCaches()
      manager.reloadPackages()
      refreshInstalledPackages()
      withContext(Dispatchers.Main) {
        handleSearch("")
      }
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
          withContext(Dispatchers.Main) {
            handleSearch(currentQuery)
          }
        }
      }
    })
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        serviceScope.launch(Dispatchers.IO) {
          initForSdk(project.modules.firstOrNull()?.pythonSdk)
        }
      }
    })

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        val newFile = event.newFile ?: return
        val module = ModuleUtilCore.findModuleForFile(newFile, project)
        val sdk = PythonSdkUtil.findPythonSdk(module) ?: return
        serviceScope.launch(Dispatchers.IO) {
          initForSdk(sdk)
        }
      }
    })
  }


  suspend fun refreshInstalledPackages() {
    val packages = manager.installedPackages.map {
      val repository = installedPackages.find { pkg -> pkg.name == it.name }?.repository ?: PyEmptyPackagePackageRepository
      InstalledPackage(it, repository)
    }

    withContext(Dispatchers.Main) {
      installedPackages = packages
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

  private fun filterPackagesForRepo(packageNames: List<String>,
                                    query: String,
                                    repository: PyPackageRepository,
                                    skipItems: Int = 0): PyPackagesViewData {

    val comparator = createNameComparator(query, repository.repositoryUrl ?: "")
    val searchResult = packageNames.asSequence()
      .filter { StringUtil.containsIgnoreCase(it, query) }
      .toList()

    val shownPackages = searchResult.asSequence()
      .sortedWith(comparator)
      .limitDisplayableResult(repository, skipItems)
    val exactMatch = shownPackages.indexOfFirst { StringUtil.equalsIgnoreCase(it.name, query) }
    return PyPackagesViewData(repository, shownPackages, exactMatch, searchResult.size - shownPackages.size)
  }

  suspend fun convertToHTML(contentType: String?, description: String): String {
    return withContext(Dispatchers.IO) {
      when (contentType) {
        "text/markdown" -> markdownToHtml(description, currentSdk!!.homeDirectory!!, project)
        "text/x-rst", "" -> rstToHtml(description, currentSdk!!)
        else -> description
      }
    }
  }

  private suspend fun rstToHtml(text: String, sdk: Sdk): String {
    val localSdk = PythonSdkType.findLocalCPythonForSdk(sdk)
    if (localSdk == null) return wrapHtml("<p>${message("python.toolwindow.packages.documentation.local.interpreter")}</p>")

    val helpersAwareTargetRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(localSdk, project)
    val targetEnvironmentRequest = helpersAwareTargetRequest.targetEnvironmentRequest
    val pythonExecution = prepareHelperScriptExecution(PythonHelper.REST_RUNNER, helpersAwareTargetRequest)

    // todo[akniazev]: this workaround should can be removed when PY-57134 is fixed
    val helperLocation = if (localSdk.sdkFlavor.getLanguageLevel(localSdk).isPython2) "py2only" else "py3only"
    val path = PythonHelpersLocator.getHelpersRoot().toPath().resolve(helperLocation)
    pythonExecution.applyHelperPackageToPythonPath(listOf(path.toString()), helpersAwareTargetRequest)

    pythonExecution.addParameter("rst2html_no_code")
    val targetProgressIndicator = TargetProgressIndicator.EMPTY
    val targetEnvironment = targetEnvironmentRequest.prepareEnvironment(targetProgressIndicator)

    targetEnvironment.uploadVolumes.entries.forEach { (_, value) ->
      value.upload(".", targetProgressIndicator)
    }

    val targetedCommandLine = pythonExecution.buildTargetedCommandLine(targetEnvironment, localSdk, emptyList())

    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val process = targetEnvironment.createProcess(targetedCommandLine, indicator)

    val commandLine = targetedCommandLine.collectCommandsSynchronously()
    val commandLineString = commandLine.joinToString(" ")

    val handler = CapturingProcessHandler(process, targetedCommandLine.charset, commandLineString)

    val output = withBackgroundProgressIndicator(project, message("python.toolwindow.packages.converting.description.progress"), cancellable = true) {
      val processInput = handler.processInput
      processInput.use {
        processInput.write(text.toByteArray())
      }
      handler.runProcess(10 * 60 * 1000)
    }

    return when {
      output.checkSuccess(thisLogger()) -> output.stdout
      else -> wrapHtml("<p>${message("python.toolwindow.packages.rst.parsing.failed")}</p>")
    }
  }

  private fun markdownToHtml(text: String, homeDir: VirtualFile, project: Project): String {
    return wrapHtml(MarkdownUtil.generateMarkdownHtml(homeDir, text, project))
  }

  override fun dispose() {
    searchJob?.cancel()
    serviceScope.cancel()
  }

  fun wrapHtml(html: String): String = "<html><head></head><body><p>$html</p></body></html>"

  fun reloadPackages() {
    serviceScope.launch(Dispatchers.IO) {
      withBackgroundProgressIndicator(project, message("python.packaging.loading.packages.progress.text"), cancellable = false) {
        manager.reloadPackages()
        manager.repositoryManager.refreshCashes()
        refreshInstalledPackages()

        withContext(Dispatchers.Main) {
          handleSearch("")
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
    val packagesFromRepository = manager.repositoryManager.packagesFromRepository(repository)

    if (currentQuery.isNotEmpty()) {
      return filterPackagesForRepo(packagesFromRepository, currentQuery, repository, skipItems)
    }
    else {
      val packagesFromRepo = packagesFromRepository.asSequence().limitDisplayableResult(repository, skipItems)
      return PyPackagesViewData(repository, packagesFromRepo, moreItems = packagesFromRepository.size - (PACKAGES_LIMIT + skipItems))
    }
  }

  private fun Sequence<String>.limitDisplayableResult(repository: PyPackageRepository, skipItems: Int = 0): List<DisplayablePackage> {
    return drop(skipItems)
      .take(PACKAGES_LIMIT)
      .map { pkg -> installedPackages.find { it.name.lowercase() == pkg.lowercase() } ?: InstallablePackage(pkg, repository) }
      .toList()
  }

  internal suspend fun moduleAttached() {
    toolWindowPanel?.recreateModulePanel()
  }

  companion object {
    private const val PACKAGES_LIMIT = 50
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