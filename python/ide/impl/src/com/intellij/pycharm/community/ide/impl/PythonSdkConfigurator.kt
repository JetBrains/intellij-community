// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.getOrInitializeModule
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.sdkConfigurator.common.enableSDKAutoConfigurator
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setSdkUsingCreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.suppressTipAndInspectionsFor
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path


class PythonSdkConfigurator : DirectoryProjectConfigurator {
  private val logger = thisLogger()

  init {
    // new SDK configurator obsoletes this engine
    if (enableSDKAutoConfigurator) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    val sdk = project.pythonSdk
    thisLogger().debug { "Input: $sdk" }
    /*
     * DataSpell skips SDK setup on new project creation, so we need to use auto-detection there. At the same time, with PyCharm we
     * first open a project and only then persist SDK from the dialog. So at this step we'll have null SDK for new projects.
     *
     * Please note, this is a dirty hack and shouldn't be used like this. We expect this whole configurator to be dropped soon, that's
     * why we're not investing time in doing it properly using ide customizations.
     */
    if (sdk != null || (isProjectCreatedWithWizard && !PlatformUtils.isDataSpell())) {
      return
    }
    if (PySdkFromEnvironmentVariable.getPycharmPythonPathProperty()?.isNotBlank() == true) {
      return
    }
    /*
     * We have an explicit SDK configuration for the welcome project, so we have to skip this configurator.
     */
    if (WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      return
    }

    val module = moduleRef.getOrInitializeModule(project, baseDir).also { thisLogger().debug { "Module: $it" } }

    StartupManager.getInstance(project).runWhenProjectIsInitialized(object : Runnable, DumbAware {
      override fun run() {
        if (module.isDisposed) return
        project.service<MyCoroutineScopeProvider>().coroutineScope.launch {
          val sdkInfos = findSuitableCreateSdkInfos(module)
          thisLogger().debug { "Suitable sdkInfos: $sdkInfos" }
          withBackgroundProgress(project, PySdkBundle.message("python.configuring.interpreter.progress"), true) {
            val lifetime = suppressTipAndInspectionsFor(module, "all suitable extensions")
            lifetime.use { configureSdk(project, module, sdkInfos) }
          }
        }
      }
    }
    )
  }

  private suspend fun findSuitableCreateSdkInfos(module: Module): List<CreateSdkInfoWithTool> = withContext(Dispatchers.Default) {
    if (!TrustedProjects.isProjectTrusted(module.project) || ApplicationManager.getApplication().isUnitTestMode) {
      emptyList()
    }
    else {
      PyProjectSdkConfigurationExtension.findAllSortedForModule(module)
    }
  }

  // TODO: PythonInterpreterService: detect and validate system python
  @ApiStatus.Internal
  suspend fun configureSdk(
    project: Project,
    module: Module,
    createSdkInfos: List<CreateSdkInfoWithTool>,
  ): Unit = withContext(Dispatchers.Default) {
    val context = UserDataHolderBase()

    if (!TrustedProjects.isProjectTrusted(project)) {
      // com.jetbrains.python.inspections.PyInterpreterInspection will ask for confirmation
      thisLogger().info("Python interpreter has not been configured since project is not trusted")
      return@withContext
    }

    excludeInnerVirtualEnvironments(module, context)


    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    if (searchPreviousUsed(module, existingSdks, project))
      return@withContext

    for (createSdkInfo in createSdkInfos) {
      if (setSdkUsingCreateSdkInfo(module, createSdkInfo, true)) return@withContext
    }

    if (setupSharedCondaEnv(module, existingSdks, project)) {
      return@withContext
    }

    if (findDefaultInterpreter(project, module)) {
      return@withContext
    }

    val systemPythons = findSortedSystemPythons(module)

    if (findPreviousUsedSdk(module, existingSdks, systemPythons)) {
      return@withContext
    }

    if (setupFallbackSdk(module)) {
      return@withContext
    }

    findSystemWideSdk(module, existingSdks, systemPythons)
  }

  private suspend fun findSystemWideSdk(
    module: Module,
    existingSdks: List<Sdk>,
    systemPythons: List<SystemPython>,
  ): Unit = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.system.interpreter"))
    thisLogger().debug("Looking for a system-wide interpreter")
    val homePaths = existingSdks
      .mapNotNull { sdk -> sdk.takeIf { !it.isTargetBased() }?.homePath?.let { homePath -> Path.of(homePath) } }
      .filter { it.getEelDescriptor() == module.project.getEelDescriptor() }
    systemPythons.firstOrNull { it.pythonBinary !in homePaths }?.let {
      thisLogger().debug { "Detected system-wide interpreter: $it" }
      withContext(Dispatchers.EDT) {
        SdkConfigurationUtil.createAndAddSDK(module.project, it.pythonBinary, PythonSdkType.getInstance())?.apply {
          thisLogger().debug { "Created system-wide interpreter: $this" }
          setReadyToUseSdk(module.project, module, this)
        }
      }
    }
  }

  private suspend fun findPreviousUsedSdk(
    module: Module,
    existingSdks: List<Sdk>,
    systemPythons: List<SystemPython>,
  ): Boolean = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.previous.system.interpreter"))
    thisLogger().debug("Looking for the previously used system-wide interpreter")
    val sdk = systemPythons.firstNotNullOfOrNull { systemPython ->
      existingSdks.firstOrNull { sdk ->
        val sdkHomePath = sdk.takeIf { !it.isTargetBased() }
          ?.homePath
          ?.let { Path.of(it) }
          ?.takeIf { it.getEelDescriptor() == module.project.getEelDescriptor() }
        sdkHomePath == systemPython.pythonBinary
      }
    } ?: return@reportRawProgress false
    thisLogger().debug { "Previously used system-wide interpreter: $sdk" }
    setReadyToUseSdk(module.project, module, sdk)
    return@reportRawProgress true
  }

  private suspend fun findSortedSystemPythons(module: Module) = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.system.pythons"))
    SystemPythonService().findSystemPythons(module.project.getEelDescriptor().toEelApi())
      .sortedWith(
        // Free-threaded Python is unstable, we don't want to have it selected by default if we have alternatives
        compareBy<SystemPython> { it.pythonInfo.freeThreaded }.thenByDescending { it.pythonInfo.languageLevel }
      )
  }

  private suspend fun findDefaultInterpreter(project: Project, module: Module): Boolean = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.default.interpreter"))
    thisLogger().debug("Looking for the default interpreter setting for a new project")
    val defaultProjectSdk = getDefaultProjectSdk() ?: return@reportRawProgress false
    thisLogger().debug { "Default interpreter setting for a new project: $defaultProjectSdk" }
    setReadyToUseSdk(project, module, defaultProjectSdk)
    true
  }

  private suspend fun setupSharedCondaEnv(
    module: Module,
    existingSdks: List<Sdk>,
    project: Project,
  ): Boolean = reportRawProgress { indicator ->
    if (!PyCondaSdkCustomizer.instance.suggestSharedCondaEnvironments) {
      return@reportRawProgress false
    }
    indicator.text(PyBundle.message("looking.for.shared.conda.environment"))
    val sharedCondaEnvs = filterSharedCondaEnvs(module, existingSdks)
    val preferred = mostPreferred(sharedCondaEnvs) ?: return@reportRawProgress false
    setReadyToUseSdk(project, module, preferred)
    return@reportRawProgress false
  }

  private suspend fun setupFallbackSdk(
    module: Module,
  ): Boolean {
    val fallback = PyCondaSdkCustomizer.instance.fallbackConfigurator
    if (fallback == null) {
      return false
    }
    val sdkCreator = fallback.checkEnvironmentAndPrepareSdkCreator(module)?.sdkCreator
    if (sdkCreator == null) {
      return false
    }
    sdkCreator(true).orLogException(logger)
    return true
  }

  private suspend fun searchPreviousUsed(
    module: Module,
    existingSdks: List<Sdk>,
    project: Project,
  ): Boolean {
    reportRawProgress { indicator ->
      indicator.text(PyBundle.message("looking.for.previous.interpreter"))
      thisLogger().debug("Looking for the previously used interpreter")
      val associatedSdks = filterAssociatedSdks(module, existingSdks)
      val preferred = mostPreferred(associatedSdks) ?: return false
      thisLogger().debug { "The previously used interpreter: $preferred" }
      setReadyToUseSdk(project, module, preferred)
    }
    return true
  }

  private suspend fun excludeInnerVirtualEnvironments(module: Module, context: UserDataHolderBase) = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.inner.venvs"))
    thisLogger().debug("Looking for inner virtual environments")
    val detectedSdks = detectAssociatedEnvironments(module, emptyList(), context)
    val moduleSdks = detectedSdks.filter { it.isLocatedInsideModule(module) }.takeIf { it.isNotEmpty() } ?: return@reportRawProgress
    withContext(Dispatchers.EDT) {
      moduleSdks.forEach {
        module.excludeInnerVirtualEnv(it)
      }
    }
  }

  private fun getDefaultProjectSdk(): Sdk? {
    return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
  }
}

@Service(Service.Level.PROJECT)
private class MyCoroutineScopeProvider(private val project: Project, val coroutineScope: CoroutineScope)
