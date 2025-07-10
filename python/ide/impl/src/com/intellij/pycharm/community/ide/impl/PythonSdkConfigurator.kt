// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setSdkUsingExtension
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.suppressTipAndInspectionsFor
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus


class PythonSdkConfigurator : DirectoryProjectConfigurator {
  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    val sdk = project.pythonSdk
    thisLogger().debug { "Input: $sdk, $isProjectCreatedWithWizard" }
    if (sdk != null || isProjectCreatedWithWizard) {
      return
    }
    if (PySdkFromEnvironmentVariable.getPycharmPythonPathProperty()?.isNotBlank() == true) {
      return
    }

    val module = getModule(moduleRef, project) ?: return


    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      PyPackageCoroutine.launch(project) {
        val extension = findExtension(module)
        val title = extension?.getIntention(module) ?: PySdkBundle.message("python.configuring.interpreter.progress")
        withBackgroundProgress(project, title, true) {
          val lifetime = extension?.let { suppressTipAndInspectionsFor(module, it) }
          lifetime.use { configureSdk(project, module, extension) }
        }
      }
    }
  }

  private suspend fun findExtension(module: Module): PyProjectSdkConfigurationExtension? = withContext(Dispatchers.Default) {
    if (!TrustedProjects.isProjectTrusted(module.project) || ApplicationManager.getApplication().isUnitTestMode) {
      null
    }
    else PyProjectSdkConfigurationExtension.EP_NAME.extensionsIfPointIsRegistered.firstOrNull {
      it.getIntention(module) != null && (!ApplicationManager.getApplication().isHeadlessEnvironment || it.supportsHeadlessModel())
    }
  }

  // TODO: PythonInterpreterService: detect and validate system python
  @ApiStatus.Internal
  suspend fun configureSdk(
    project: Project,
    module: Module,
    extension: PyProjectSdkConfigurationExtension?,
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

    if (findRelatedSdk(module, existingSdks, context))
      return@withContext

    if (extension != null) {
      val isExtensionSetup = setSdkUsingExtension(module, extension) {
        withContext(Dispatchers.Default) {
          extension.createAndAddSdkForConfigurator(module)
        }
      }
      if (isExtensionSetup) return@withContext
    }

    if (setupSharedCondaEnv(module, existingSdks, project)) {
      return@withContext
    }

    if (findDefaultInterpreter(project, module)) {
      return@withContext
    }

    if (findPreviousUsedSdk(existingSdks, project, module)) {
      return@withContext
    }

    findSystemWideSdk(module, existingSdks, context, project)
  }

  private suspend fun findSystemWideSdk(
    module: Module,
    existingSdks: List<Sdk>,
    context: UserDataHolderBase,
    project: Project,
  ) = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.system.interpreter"))
    thisLogger().debug("Looking for a system-wide interpreter")
    detectSystemWideSdks(module, existingSdks, context).firstOrNull()?.let {
      thisLogger().debug { "Detected system-wide interpreter: $it" }
      withContext(Dispatchers.EDT) {
        SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
          thisLogger().debug { "Created system-wide interpreter: $this" }
          setReadyToUseSdk(project, module, this)
        }
      }
    }
  }

  private suspend fun findPreviousUsedSdk(
    existingSdks: List<Sdk>,
    project: Project,
    module: Module,
  ): Boolean = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.previous.system.interpreter"))
    thisLogger().debug("Looking for the previously used system-wide interpreter")
    val sdk = mostPreferred(filterSystemWideSdks(existingSdks)) ?: return@reportRawProgress false
    thisLogger().debug { "Previously used system-wide interpreter: $sdk" }
    setReadyToUseSdk(project, module, sdk)
    return@reportRawProgress true
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

  private suspend fun findRelatedSdk(
    module: Module,
    existingSdks: List<Sdk>,
    context: UserDataHolderBase,
  ): Boolean = reportRawProgress { indicator ->
    indicator.text(PyBundle.message("looking.for.related.venv"))
    thisLogger().debug("Looking for a virtual environment related to the project")
    val env = detectAssociatedEnvironments(module, existingSdks, context).firstOrNull() ?: return@reportRawProgress false

    env.setupSdk(module, existingSdks, true)
    true
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

  private fun getModule(moduleRef: Ref<Module>, project: Project): Module? {
    val module = (moduleRef.get() ?: ModuleManager.getInstance(project).modules.firstOrNull())
    return module.also { thisLogger().debug { "Module: $it" } }
  }

  private fun getDefaultProjectSdk(): Sdk? {
    return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
  }
}
