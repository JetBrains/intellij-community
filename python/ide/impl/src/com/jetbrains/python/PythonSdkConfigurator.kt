// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setSdkUsingExtension
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.suppressTipAndInspectionsFor
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension

/**
 * @author vlan
 */
internal class PythonSdkConfigurator : DirectoryProjectConfigurator {
  companion object {
    private val LOGGER = Logger.getInstance(PythonSdkConfigurator::class.java)

    private fun getModule(moduleRef: Ref<Module>, project: Project): Module? {
      val module = (moduleRef.get() ?: ModuleManager.getInstance(project).modules.firstOrNull())
      return module.also { LOGGER.debug { "Module: $it" } }
    }

    private fun getDefaultProjectSdk(): Sdk? {
      return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
    }

    private fun <T> guardIndicator(indicator: ProgressIndicator, computable: () -> T): T {
      return ProgressManager.getInstance().runProcess(computable, SensitiveProgressWrapper(indicator))
    }
  }

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    val sdk = project.pythonSdk
    LOGGER.debug { "Input: $sdk, $isProjectCreatedWithWizard" }
    if (sdk != null || isProjectCreatedWithWizard) {
      return
    }

    val module = getModule(moduleRef, project) ?: return
    val extension = findExtension(module)
    val lifetime = extension?.let { suppressTipAndInspectionsFor(module, it) }

    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      ProgressManager.getInstance().run(
        object : Task.Backgroundable(project, PySdkBundle.message("python.configuring.interpreter.progress"), extension == null) {
          override fun run(indicator: ProgressIndicator) = lifetime.use { configureSdk(project, module, extension, indicator) }
        }
      )
    }
  }

  private fun findExtension(module: Module): PyProjectSdkConfigurationExtension? {
    return if (!module.project.isTrusted() || ApplicationManager.getApplication().let { it.isHeadlessEnvironment || it.isUnitTestMode }) {
      null
    }
    else PyProjectSdkConfigurationExtension.EP_NAME.findFirstSafe { it.getIntention(module) != null }
  }

  private fun configureSdk(project: Project,
                           module: Module,
                           extension: PyProjectSdkConfigurationExtension?,
                           indicator: ProgressIndicator) {
    // please keep this method in sync with com.jetbrains.python.inspections.PyInterpreterInspection.Visitor.getSuitableSdkFix

    indicator.isIndeterminate = true

    val context = UserDataHolderBase()

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.inner.venvs")
    LOGGER.debug("Looking for inner virtual environments")
    guardIndicator(indicator) {
      detectAssociatedEnvironments(module, emptyList(), context).filter { it.isLocatedInsideModule(module) }.takeIf { it.isNotEmpty() }
    }?.let {
      runInEdt { it.forEach { module.excludeInnerVirtualEnv(it) } }
    }

    if (!project.isTrusted()) {
      // com.jetbrains.python.inspections.PyInterpreterInspection will ask for confirmation
      LOGGER.info("Python interpreter has not been configured since project is not trusted")
      return
    }

    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.previous.interpreter")
    LOGGER.debug("Looking for the previously used interpreter")
    guardIndicator(indicator) { mostPreferred(filterAssociatedSdks(module, existingSdks)) }?.let {
      LOGGER.debug { "The previously used interpreter: $it" }
      setReadyToUseSdk(project, module, it)
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.related.venv")
    LOGGER.debug("Looking for a virtual environment related to the project")
    guardIndicator(indicator) { detectAssociatedEnvironments(module, existingSdks, context).firstOrNull() }?.let {
      LOGGER.debug { "Detected virtual environment related to the project: $it" }
      val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
      LOGGER.debug { "Created virtual environment related to the project: $newSdk" }

      runInEdt {
        SdkConfigurationUtil.addSdk(newSdk)
        newSdk.associateWithModule(module, null)
        setReadyToUseSdk(project, module, newSdk)
      }

      return
    }

    if (indicator.isCanceled) return

    if (extension != null) {
      indicator.text = ""
      setSdkUsingExtension(module, extension) { extension.createAndAddSdkForConfigurator(module) }
      return
    }

    if (indicator.isCanceled) return

    if (PyCondaSdkCustomizer.instance.suggestSharedCondaEnvironments) {
      indicator.text = PyBundle.message("looking.for.shared.conda.environment")
      guardIndicator(indicator) { mostPreferred(filterSharedCondaEnvs(module, existingSdks)) }?.let {
        setReadyToUseSdk(project, module, it)
        return
      }

      guardIndicator(indicator) { detectCondaEnvs(module, existingSdks, context).firstOrNull() }?.let {
        val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
        runInEdt {
          SdkConfigurationUtil.addSdk(newSdk)
          setReadyToUseSdk(project, module, newSdk)
        }
        return
      }

      if (indicator.isCanceled) return
    }

    indicator.text = PyBundle.message("looking.for.default.interpreter")
    LOGGER.debug("Looking for the default interpreter setting for a new project")
    guardIndicator(indicator) { getDefaultProjectSdk() }?.let {
      LOGGER.debug { "Default interpreter setting for a new project: $it" }
      setReadyToUseSdk(project, module, it)
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.previous.system.interpreter")
    LOGGER.debug("Looking for the previously used system-wide interpreter")
    guardIndicator(indicator) { mostPreferred(filterSystemWideSdks(existingSdks)) }?.let {
      LOGGER.debug { "Previously used system-wide interpreter: $it" }
      setReadyToUseSdk(project, module, it)
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.system.interpreter")
    LOGGER.debug("Looking for a system-wide interpreter")
    guardIndicator(indicator) { detectSystemWideSdks(module, existingSdks, context).firstOrNull() }?.let {
      LOGGER.debug { "Detected system-wide interpreter: $it" }
      runInEdt {
        SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
          LOGGER.debug { "Created system-wide interpreter: $this" }
          setReadyToUseSdk(project, module, this)
        }
      }
    }
  }
}
