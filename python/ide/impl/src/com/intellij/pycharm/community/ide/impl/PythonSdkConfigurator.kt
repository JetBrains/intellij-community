// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * @see [PyConfigureSdkOnWslTest]
 */
class PythonSdkConfigurator : DirectoryProjectConfigurator {
  companion object {
    private val LOGGER = Logger.getInstance(PythonSdkConfigurator::class.java)

    private fun getModule(moduleRef: Ref<Module>, project: Project): Module? {
      val module = (moduleRef.get() ?: ModuleManager.getInstance(project).modules.firstOrNull())
      return module.also { LOGGER.debug { "Module: $it" } }
    }

    private fun getDefaultProjectSdk(): Sdk? {
      return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
    }

  }

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    val sdk = project.pythonSdk
    LOGGER.debug { "Input: $sdk, $isProjectCreatedWithWizard" }
    if (sdk != null || isProjectCreatedWithWizard) {
      return
    }
    if (PySdkFromEnvironmentVariable.getPycharmPythonPathProperty()?.isNotBlank() == true) {
      return
    }

    val module = getModule(moduleRef, project) ?: return


    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      PyPackageCoroutine.launch(project) {
        withBackgroundProgress(project, PySdkBundle.message("python.configuring.interpreter.progress"), true) {
          val extension = findExtension(module)
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
    reportRawProgress { indicator ->
      // please keep this method in sync with com.jetbrains.python.inspections.PyInterpreterInspection.Visitor.getSuitableSdkFix

      indicator.fraction(null)

      val context = UserDataHolderBase()

      if (!coroutineContext.isActive) return@reportRawProgress

      indicator.text(PyBundle.message("looking.for.inner.venvs"))
      LOGGER.debug("Looking for inner virtual environments")
      detectAssociatedEnvironments(module, emptyList(), context).filter { it.isLocatedInsideModule(module) }.takeIf { it.isNotEmpty() }?.let {
        withContext(Dispatchers.EDT) { it.forEach { module.excludeInnerVirtualEnv(it) } }
      }

      if (!TrustedProjects.isProjectTrusted(project)) {
        // com.jetbrains.python.inspections.PyInterpreterInspection will ask for confirmation
        LOGGER.info("Python interpreter has not been configured since project is not trusted")
        return@reportRawProgress
      }

      val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

      if (!coroutineContext.isActive) return@reportRawProgress

      indicator.text(PyBundle.message("looking.for.previous.interpreter"))
      LOGGER.debug("Looking for the previously used interpreter")
      mostPreferred(filterAssociatedSdks(module, existingSdks))?.let {
        LOGGER.debug { "The previously used interpreter: $it" }
        setReadyToUseSdk(project, module, it)
        return@reportRawProgress
      }

      if (!coroutineContext.isActive) return@reportRawProgress

      indicator.text(PyBundle.message("looking.for.related.venv"))
      LOGGER.debug("Looking for a virtual environment related to the project")
      val env = detectAssociatedEnvironments(module, existingSdks, context).firstOrNull()

      if (env != null) {
        env.setupSdk(module, existingSdks, true)
        return@reportRawProgress
      }

      if (!coroutineContext.isActive) return@reportRawProgress

      if (extension != null) {
        indicator.text(extension.getIntention(module) ?: "")
        setSdkUsingExtension(module, extension) { extension.createAndAddSdkForConfigurator(module) }
        return@reportRawProgress
      }

      if (!coroutineContext.isActive) return@reportRawProgress

      if (PyCondaSdkCustomizer.instance.suggestSharedCondaEnvironments) {
        indicator.text(PyBundle.message("looking.for.shared.conda.environment"))
        mostPreferred(filterSharedCondaEnvs(module, existingSdks))?.let {
          setReadyToUseSdk(project, module, it)
          return@reportRawProgress
        }

        if (!coroutineContext.isActive) return@reportRawProgress
      }

      indicator.text(PyBundle.message("looking.for.default.interpreter"))
      LOGGER.debug("Looking for the default interpreter setting for a new project")
      getDefaultProjectSdk()?.let {
        LOGGER.debug { "Default interpreter setting for a new project: $it" }
        setReadyToUseSdk(project, module, it)
        return@reportRawProgress
      }

      if (!coroutineContext.isActive) return@reportRawProgress

      indicator.text(PyBundle.message("looking.for.previous.system.interpreter"))
      LOGGER.debug("Looking for the previously used system-wide interpreter")
      mostPreferred(filterSystemWideSdks(existingSdks))?.let {
        LOGGER.debug { "Previously used system-wide interpreter: $it" }
        setReadyToUseSdk(project, module, it)
        return@reportRawProgress
      }

      if (!coroutineContext.isActive) return@reportRawProgress

      indicator.text(PyBundle.message("looking.for.system.interpreter"))
      LOGGER.debug("Looking for a system-wide interpreter")
      detectSystemWideSdks(module, existingSdks, context).firstOrNull()?.let {
        LOGGER.debug { "Detected system-wide interpreter: $it" }
        withContext(Dispatchers.EDT) {
          SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
            LOGGER.debug { "Created system-wide interpreter: $this" }
            setReadyToUseSdk(project, module, this)
          }
        }
      }
    }
  }
}
