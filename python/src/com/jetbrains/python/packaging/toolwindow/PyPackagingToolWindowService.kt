// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.google.gson.Gson
import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.*
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil

@Service
class PyPackagingToolWindowService(val project: Project) : Disposable {

  private lateinit var toolWindowPanel: PyPackagingToolWindowPanel
  private var installedPackages: List<InstalledPackage> = emptyList()
  private var currentSdk: Sdk? = null
  private var selectedPackage: DisplayablePackage? = null
  private var selectedInfo: PackageInfo? = null
  private var currentJob: Job? = null
  private var searchJob: Job? = null
  private var currentQuery: String = ""
  private val gson = Gson()


  fun initialize(toolWindowPanel: PyPackagingToolWindowPanel) {
    this.toolWindowPanel = toolWindowPanel
    initForSdk(project.modules.firstOrNull()?.pythonSdk)
    subscribeToChanges()
  }

  fun packageSelected(pkg: DisplayablePackage) {
    currentJob?.cancel()
    selectedPackage = pkg
    currentJob = GlobalScope.launch(Dispatchers.Default) {
      val response = fetchPackageInfo(selectedPackage!!.name)
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
        selectedInfo = EMPTY_INFO
      }

      if (isActive) {
        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          toolWindowPanel.displaySelectedPackage(selectedPackage!!, selectedInfo!!)
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
        val pypi = PyPIPackageCache.getInstance().packageNames.asSequence()
          .filter { StringUtil.containsIgnoreCase(it, query) }
          .map { pkg -> installed.find { it.name == pkg } ?: InstallablePackage (pkg) }
          .toList()

        val exactMatch = pypi.indexOfFirst { StringUtil.equalsIgnoreCase(query, it.name) }
        if (isActive) {
          withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
            toolWindowPanel.showSearchResult(installed, pypi, exactMatch)
          }
        }
      }
    }
    else {
      toolWindowPanel.resetSearch(installedPackages)
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
          val newFiltered = newPackages.let { packages ->
            if (currentQuery.isNotEmpty()) packages.filter { StringUtil.containsIgnoreCase(it.name, currentQuery) } else packages
          }

          if (toInstall.name == selectedPackage?.name) {
            val installed = installedPackages.find { it.name == toInstall.name }
            if (installed == null) {
              toolWindowPanel.showInstallableControls()
            } else {
              selectedPackage = installed
              toolWindowPanel.packageInstalled(installed, newFiltered)
            }
          }
          else {
            toolWindowPanel.updateInstalledPackagesTable(newFiltered)
          }
        }
      }
    }

    managementService.installPackage(RepoPackage(toInstall.name, null), version, false, null, listener, false)
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
          if (packageToDelete.name == selectedPackage?.name) {
            val stillInstalled = installedPackages.find { it.name == packageToDelete.name }
            if (stillInstalled != null) {
              toolWindowPanel.showInstalledControls()
              // todo: add toolwindow notification and remove balloons from PackageManagementService
            }
            else {
              val installablePackage = InstallablePackage(packageToDelete.name)
              selectedPackage = installablePackage
              toolWindowPanel.packageDeleted(installablePackage)
            }
          }
        }
      }
    }
    managementService.uninstallPackages(listOf(packageToDelete.instance), listener)
  }

  private fun initForSdk(sdk: Sdk?) {
    currentSdk = sdk
    if (currentSdk != null) {
      collectInstalledPackages(resetSearch = true)
    }
    toolWindowPanel.contentVisible = currentSdk != null
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
        packages = PyPackageManagers.getInstance().forSdk(sdk).refreshAndGetPackages(false).map { InstalledPackage(it) }
        newPackages = packages?.filter { it.name !in currentlyInstalled }
      }

      override fun onSuccess() {
        installedPackages = packages ?: error("No installed packages found")
        if (resetSearch) toolWindowPanel.resetSearch(installedPackages)
        callback?.invoke(newPackages ?: emptyList())
      }
    }
    ProgressManager.getInstance().run(task)
  }

  private suspend fun fetchPackageInfo(packageName: String): String? = withContext(Dispatchers.IO) {
    val result = runCatching {
      HttpRequests.request("https://pypi.org/pypi/${packageName}/json").readTimeout(3000).readString()
    }
    if (result.isFailure) thisLogger().debug("Request failed for package $packageName")
    result.getOrNull()
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

  companion object {
    private val EMPTY_INFO = PackageInfo(null, PyPackagingToolWindowPanel.REQUEST_FAILED_TEXT, emptyList())
  }
}