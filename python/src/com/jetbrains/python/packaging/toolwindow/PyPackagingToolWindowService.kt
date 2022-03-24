// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.google.gson.Gson
import com.intellij.ProjectTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.HttpRequests
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.PyBundle.*
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.PyPackageVersionComparator.STR_COMPARATOR
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.*
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil

@Service
class PyPackagingToolWindowService(val project: Project) : Disposable {

  private lateinit var toolWindowPanel: PyPackagingToolWindowPanel
  private var installedPackages: List<InstalledPackage> = emptyList()
  internal var currentSdk: Sdk? = null
  private var selectedPackage: DisplayablePackage? = null
  private var selectedInfo: PackageInfo? = null
  private var currentJob: Job? = null
  private var searchJob: Job? = null
  private var currentQuery: String = ""
  private val gson = Gson()


  fun initialize(toolWindowPanel: PyPackagingToolWindowPanel) {
    this.toolWindowPanel = toolWindowPanel
    initForSdk(project.modules.firstOrNull()?.pythonSdk)
    GlobalScope.launch(Dispatchers.IO) {
      val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk) as PyPackageManagementService
      managementService.allPackages // load packages
      handleSearch("")
    }
    subscribeToChanges()
  }

  fun packageSelected(pkg: DisplayablePackage) {
    currentJob?.cancel()
    selectedPackage = pkg
    toolWindowPanel.showHeaderForPackage(pkg)
    currentJob = GlobalScope.launch(Dispatchers.Default) {
      if (PythonSdkUtil.isRemote(currentSdk)) {
        selectedInfo = REMOTE_INTERPRETER_INFO
      }
      else {
        val response = fetchPackageInfo(selectedPackage!!)
        if (response != null) {
          val packageDetails = gson.fromJson(response, PyPIPackageUtil.PackageDetails::class.java)

          selectedInfo = with(packageDetails.info) {
            val renderedDescription  = when {
              description.isNotEmpty() -> convertToHTML(descriptionContentType, description)
              summary.isNotEmpty() -> wrapHtml(summary)
              else -> PyPackagingToolWindowPanel.NO_DESCRIPTION
            }
            PackageInfo(projectUrls["Documentation"],
                        renderedDescription,
                        packageDetails.releases.sortedWith(STR_COMPARATOR.reversed()))
          }
        }
        else {
          // try to get version from repository
          val versionFromRepo = fetchVersionsFromPage(selectedPackage!!)
          if (versionFromRepo.isNotEmpty()) {
            selectedInfo = PackageInfo(null,
                                       wrapHtml("<p>${message("python.toolwindow.packages.no.documentation")}</p>"),
                                       versionFromRepo)
          }
          else {
            selectedInfo = EMPTY_INFO
          }
        }
      }

      if (isActive) {
        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          toolWindowPanel.displaySelectedPackageInfo(selectedInfo!!)
        }
      }
    }
  }

  fun handleSearch(query: String) {
    currentQuery = query
    if (query.isNotEmpty()) {
      searchJob?.cancel()
      searchJob = GlobalScope.launch(Dispatchers.Default) {
        val installed = installedPackages.filter { StringUtil.containsIgnoreCase(it.name, query) }
        val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk) as PyPackageManagementService
        val invalidRepositories = service<PyPackageRepositories>().invalidRepositories.map(::PyInvalidRepositoryViewData)

        val packagesFromRepos = managementService.allPackagesByRepository.map {
          filterPackagesForRepo(it.value, query, it.key)
        }

        if (isActive) {
          withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
            toolWindowPanel.showSearchResult(installed, packagesFromRepos + invalidRepositories)
          }
        }
      }
    }
    else {
      val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk) as PyPackageManagementService
      val packagesFromRepos = managementService.allPackagesByRepository.map { entry ->
        val repository = service<PyPackageRepositories>()
          .repositories
          .find { repo -> repo.repositoryUrl == entry.key } ?: PyPIPackageRepository

        val (packagesSeq, size) = when {
          PyPIPackageUtil.isPyPIRepository(entry.key) -> Pair(PyPIPackageRanking.names, PyPIPackageCache.getInstance().packageNames.size)
          else -> Pair(entry.value.asSequence().map { it.name }, entry.value.size)
        }

        val shownPackages = packagesSeq.limitDisplayableResult(repository)
        PyPackagesViewData(repository, shownPackages, moreItems = size - PACKAGES_LIMIT)
      }
      val invalidRepositories = service<PyPackageRepositories>().invalidRepositories.map(::PyInvalidRepositoryViewData)

      toolWindowPanel.resetSearch(installedPackages, packagesFromRepos + invalidRepositories)
    }
  }

  fun installSelectedPackage(version: String?) {
    val toInstall = selectedPackage as? InstallablePackage ?: return
    val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk)
    val listener = object : PackageManagementService.Listener {
      override fun operationStarted(packageName: String?) {
        toolWindowPanel.startProgress()
      }

      override fun operationFinished(packageName: String?, errorDescription: PackageManagementService.ErrorDescription?) {
        toolWindowPanel.stopProgress()
        collectInstalledPackages { newPackages ->
          val withRepo = newPackages.map { InstalledPackage(it.instance, toInstall.repository) }
          installedPackages = installedPackages.filterNot { it in newPackages } + withRepo

          if (currentQuery.isNotEmpty()) {
            val newFiltered = withRepo.filter { StringUtil.containsIgnoreCase(it.name, currentQuery) }
            toolWindowPanel.packageInstalled(newFiltered)
          }
          else {
            toolWindowPanel.packageInstalled(withRepo)
          }

        }
      }
    }

    managementService.installPackage(RepoPackage(toInstall.name, toInstall.repository.urlForInstallation), version, false, null, listener, false)
  }

  fun deleteSelectedPackage() {
    val packageToDelete = selectedPackage as? InstalledPackage ?: return
    val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk)
    val listener = object : PackageManagementService.Listener {
      override fun operationStarted(packageName: String?) {
        toolWindowPanel.startProgress()
      }

      override fun operationFinished(packageName: String?, errorDescription: PackageManagementService.ErrorDescription?) {
        toolWindowPanel.stopProgress()
        collectInstalledPackages {
          toolWindowPanel.packageDeleted(packageToDelete)
          if (packageToDelete.name == selectedPackage?.name) {
            selectedPackage = null
            toolWindowPanel.setEmpty()
          }
        }
      }
    }
    managementService.uninstallPackages(listOf(packageToDelete.instance), listener)
  }

  private fun initForSdk(sdk: Sdk?) {
    val previousSdk = currentSdk
    currentSdk = sdk
    if (currentSdk != null) {
      collectInstalledPackages(resetSearch = true)
    }
    toolWindowPanel.contentVisible = currentSdk != null
    if (currentSdk == null || currentSdk != previousSdk) {
      selectedPackage = null
      toolWindowPanel.setEmpty()
    }
  }

  private fun subscribeToChanges() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, PyPackageManager.Listener {
      if (currentSdk == it) collectInstalledPackages(resetSearch = currentQuery.isEmpty())
    })
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        initForSdk(project.modules.firstOrNull()?.pythonSdk)
      }
    })
  }

  private fun collectInstalledPackages(resetSearch: Boolean = false, callback: ((List<InstalledPackage>) -> Unit)? = null) {
    val task = object : Task.Backgroundable(project, message("python.toolwindow.packages.collecting.packages.task.title")) {
      val sdk = currentSdk!!
      val previouslyInstalled = installedPackages
      var packages: List<InstalledPackage>? = null
      var newPackages: List<InstalledPackage>? = null
      override fun run(indicator: ProgressIndicator) {
        val currentlyInstalled = previouslyInstalled.mapTo(HashSet()) { it.name }
        packages = PyPackageManagers.getInstance().forSdk(sdk).refreshAndGetPackages(false).map {
          val repository = previouslyInstalled.find { pkg -> pkg.name == it.name }?.repository ?: PyEmptyPackagePackageRepository
          InstalledPackage(it, repository)
        }
        newPackages = packages?.filter { it.name !in currentlyInstalled }
      }

      override fun onSuccess() {
        installedPackages = packages ?: error("No installed packages found")
        if (resetSearch) handleSearch("")
        callback?.invoke(newPackages ?: emptyList())
      }
    }
    ProgressManager.getInstance().run(task)
  }

  private suspend fun fetchPackageInfo(pkg: DisplayablePackage): String? = withContext(Dispatchers.IO) {
    val result = runCatching {
      val repoUrl = pkg.repository.repositoryUrl.let { if (it.isNullOrEmpty()) PyPIPackageUtil.PYPI_LIST_URL else it }
      val packageUrl = repoUrl.replace("simple", "pypi/${pkg.name}/json")
      HttpRequests.request(packageUrl)
        .withBasicAuthorization(pkg.repository)
        .readTimeout(3000)
        .readString()
    }
    if (result.isFailure) thisLogger().debug("Request failed for package $pkg.name")
    result.getOrNull()
  }

  private fun filterPackagesForRepo(packageNames: List<RepoPackage>,
                                    query: String,
                                    url: String,
                                    skipItems: Int = 0): PyPackagesViewData {

    val comparator = createComparator(query, url)

    val repository = service<PyPackageRepositories>().findByUrl(url) ?: PyPIPackageRepository
    val searchResult = packageNames.asSequence()
      .filter { StringUtil.containsIgnoreCase(it.name, query) }
      .toList()

    val shownPackages = searchResult.asSequence()
      .sortedWith(comparator)
      .map { it.name }
      .limitDisplayableResult(repository, skipItems)

    val exactMatch = shownPackages.indexOfFirst { StringUtil.equalsIgnoreCase(it.name, query) }
    return PyPackagesViewData(repository, shownPackages, exactMatch, searchResult.size - shownPackages.size)
  }


  private suspend fun fetchVersionsFromPage(pkg: DisplayablePackage): List<String> = withContext(Dispatchers.IO) {
    val result = runCatching {
      val url = StringUtil.trimEnd(pkg.repository.repositoryUrl!!, "/") + "/" + pkg.name
      PyPIPackageUtil.parsePackageVersionsFromArchives(url, pkg.name)
    }
    return@withContext result.getOrDefault(emptyList()).sortedWith(STR_COMPARATOR.reversed())
  }

  private fun convertToHTML(contentType: String, description: String): String {
    return when (contentType) {
      "text/markdown" -> markdownToHtml(description, currentSdk!!.homeDirectory!!, project)
      "text/x-rst", "" -> rstToHtml(description, currentSdk!!)
      else -> description
    }
  }

  private fun rstToHtml(text: String, sdk: Sdk): String {
    val commandLine = PythonHelper.REST_RUNNER.newCommandLine(sdk, listOf("rst2html_no_code"))
    val output = PySdkUtil.getProcessOutput(commandLine, sdk.homeDirectory!!.parent.path, null, 5000,
                                            text.toByteArray(Charsets.UTF_8), false)
    return when {
      output.checkSuccess(thisLogger()) -> output.stdout
      else -> wrapHtml("<p>${message("python.toolwindow.packages.rst.parsing.failed")}</p>")
    }
  }

  private fun markdownToHtml(text: String, homeDir: VirtualFile, project: Project): String {
    return wrapHtml(MarkdownUtil.generateMarkdownHtml(homeDir, text, project))
  }

  override fun dispose() {
    currentJob?.cancel()
    searchJob?.cancel()
  }

  private fun wrapHtml(html: String): String = "<html><head></head><body><p>$html</p></body></html>"

  fun reloadPackages() {
    GlobalScope.launch(Dispatchers.IO) {
      withBackgroundProgressIndicator(project, message("python.packaging.loading.packages.progress.text"), cancellable = false) {
        val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk)
        PyPIPackageUtil.INSTANCE.loadAdditionalPackages(managementService.allRepositories!!, true)
        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          handleSearch("")
        }
      }
    }
  }

  fun manageRepositories() {
    val updated = SingleConfigurableEditor(project, PyRepositoriesList(project)).showAndGet()
    if (updated) {
      GlobalScope.launch(Dispatchers.IO) {
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
    val managementService = PyPackageManagers.getInstance().getManagementService(project, currentSdk) as PyPackageManagementService
    val fromCurrentRepo = managementService.allPackagesByRepository[repository.repositoryUrl]!!
    if (currentQuery.isNotEmpty()) {
      return filterPackagesForRepo(fromCurrentRepo, currentQuery, repository.repositoryUrl!!, skipItems)
    }
    else {
      // The number of items to skip might be more than the number of ranked packages we store,
      // so we need to include the remaining packages from pypi, filtering out those, that are already shown.
      if (PyPIPackageUtil.isPyPIRepository(repository.repositoryUrl)) {
        val ranked = PyPIPackageRanking.packageRank
        val names = PyPIPackageRanking.names
        val rankedSize = PyPIPackageRanking.packageRank.size
        val pypiAdjusted = when {
          skipItems > rankedSize -> fromCurrentRepo.asSequence().drop(skipItems - rankedSize).map { it.name }
          skipItems + PACKAGES_LIMIT > rankedSize -> {
            val pypiRemaining = fromCurrentRepo.asSequence()
              .map { it.name }
              .filterNot { it in ranked }

            names.drop(skipItems) + pypiRemaining
          }
          else -> names.drop(skipItems)
        }


        val pypiPackages = pypiAdjusted.limitDisplayableResult(repository)

        val packageNum = PyPIPackageCache.getInstance().packageNames.size
        return PyPackagesViewData(repository, pypiPackages, moreItems = packageNum - PACKAGES_LIMIT)
      }

      val packagesFromRepo = fromCurrentRepo.asSequence()
        .map { it.name }
        .limitDisplayableResult(repository, skipItems)

      return PyPackagesViewData(repository, packagesFromRepo, moreItems = fromCurrentRepo.size - (PACKAGES_LIMIT + skipItems))
    }
  }

  private fun Sequence<String>.limitDisplayableResult(repository: PyPackageRepository, skipItems: Int = 0): List<DisplayablePackage> {
    return drop(skipItems)
      .take(PACKAGES_LIMIT)
      .map { pkg -> installedPackages.find { it.name == pkg } ?: InstallablePackage(pkg, repository) }
      .toList()
  }

  fun installFromLocation(location: String, editable: Boolean) {
    val ui = PyPackageManagerUI(project, currentSdk!!, object : PyPackageManagerUI.Listener {
      override fun started() {}

      override fun finished(exceptions: MutableList<ExecutionException>?) {
        handleSearch("")
      }
    })
    val installOptions = if (editable) listOf("-e", location) else listOf(location)

    ui.install(listOf(PyRequirementImpl(location, emptyList(), installOptions, "")), emptyList())
  }


  companion object {
    private val EMPTY_INFO = PackageInfo(null, PyPackagingToolWindowPanel.REQUEST_FAILED_TEXT, emptyList())
    private val REMOTE_INTERPRETER_INFO = PackageInfo(null, PyPackagingToolWindowPanel.REMOTE_INTERPRETER_TEXT, emptyList())
    private const val PACKAGES_LIMIT = 50

    private fun createComparator(query: String, url: String): Comparator<RepoPackage> {
      val nameComparator = Comparator<RepoPackage> { o1, o2 ->
        val name1 = o1.name.toLowerCase()
        val name2 = o2.name.toLowerCase()
        val queryLowerCase = query.toLowerCase()
        return@Comparator when {
          name1.startsWith(queryLowerCase) && name2.startsWith(queryLowerCase) -> name1.length - name2.length
          name1.startsWith(queryLowerCase) -> -1
          name2.startsWith(queryLowerCase) -> 1
          else -> name1.compareTo(name2)
        }
      }

      if (PyPIPackageUtil.isPyPIRepository(url)) {
        val ranking = PyPIPackageRanking.packageRank
        return Comparator { p1, p2 ->
          val rank1 = ranking[p1.name.toLowerCase()]
          val rank2 = ranking[p2.name.toLowerCase()]
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