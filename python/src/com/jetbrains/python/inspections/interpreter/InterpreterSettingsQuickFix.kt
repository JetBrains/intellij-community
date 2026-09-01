// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.DataManager
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.model.api.CreateSdkNotFilesResult
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkConfigurationError
import com.intellij.python.pyproject.model.api.SdkConfigurationResult
import com.intellij.python.pyproject.model.api.autoConfigureSdkDoNotCreateFiles
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.performToolInstallation
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.impl.getRootModuleOrNull
import com.jetbrains.python.inspections.InspectionRunnerResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.configuration.suppressors.suppressTipAndInspectionsFor
import com.jetbrains.python.sdk.configurePythonSdk
import com.intellij.python.sdk.backend.PySdkBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Executor that accepts at most one concurrent task.
 * While a task is running, subsequent submissions are discarded.
 */
@ApiStatus.Internal
interface BusyGuardExecutor {
  /**
   * Reflects whether an action is currently being executed.
   * Can be used to update UI accordingly (e.g., replacing action links with a progress indicator).
   */
  val isBusy: StateFlow<Boolean>

  /**
   * Submits an action for execution.
   * If another action is already in progress, the submission is silently discarded.
   */
  fun execute(action: suspend () -> Unit)
}

/**
 * Provides an [ActionLink] for the "no Python interpreter" editor notification banner.
 *
 * Implementations are discovered asynchronously by [PyAsyncFileInspectionRunner][com.jetbrains.python.inspections.PyAsyncFileInspectionRunner]
 * and rendered inside [PyInterpreterNotificationProvider].
 * Long-running work (SDK creation, tool installation) must be submitted through the supplied [BusyGuardExecutor]
 * so that all notification panels share the same busy state.
 *
 * **WARNING:** Implementations must NOT hold strong references to [Module] or [Project] in their fields.
 * Instances are cached by [PyAsyncFileInspectionRunner] with the Module as a weak key.
 * A strong reference from the fix back to the Module prevents the weak key from being collected,
 * causing a project leak after the project is closed. Use the [module] and [project] parameters
 * passed to [createActionLink] instead.
 */
@ApiStatus.Internal
interface InterpreterFix {
  fun createActionLink(
    module: Module,
    project: Project,
    psiFile: PsiFile,
    executor: BusyGuardExecutor,
  ): ActionLink
}

class InterpreterSettingsQuickFix(private val myModule: Module?) : LocalQuickFix {
  override fun getFamilyName(): String = if (PlatformUtils.isPyCharm())
    PyBundle.message("python.sdk.interpreter.settings")
  else
    PyBundle.message("python.sdk.configure.python.interpreter")

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    showPythonInterpreterSettings(project, myModule)
  }

  companion object {
    fun showPythonInterpreterSettings(project: Project, module: Module?) {
      val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)
      if (ConfigurableVisitor.findById(PyActiveSdkModuleConfigurable.CONFIGURABLE_ID, listOf(group)) != null) {
        ShowSettingsUtilImpl.showSettingsDialog(project, PyActiveSdkModuleConfigurable.CONFIGURABLE_ID, null)
        return
      }

      val settingsService = ProjectSettingsService.getInstance(project)
      if (module == null || justOneModuleInheritingSdk(project, module)) {
        settingsService.openProjectSettings()
      }
      else {
        settingsService.openModuleSettings(module)
      }
    }

    private fun justOneModuleInheritingSdk(project: Project, module: Module): Boolean {
      return ProjectRootManager.getInstance(project).projectSdk == null &&
             ModuleRootManager.getInstance(module).isSdkInherited &&
             ModuleManager.getInstance(project).modules.size < 2
    }
  }
}

internal fun createInterpreterCacheLoader(): suspend (Module) -> InspectionRunnerResult = { module ->
  val fixes = when (val r = module.getQuickFixBySdkSuggestion()) {
    is FindQuickFixResult.SdkAppliedAutomatically -> emptyList()
    is FindQuickFixResult.ShowUserFix -> buildList {
      r.fix?.let { add(it) }
      add(ConfigureInterpreterFix())
    }
  }
  InspectionRunnerResult(fixes, shouldCache = true)
}

private class ConfigureInterpreterFix : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return DropDownLink(PyBundle.message("python.sdk.custom.environment")) {
      val context = DataManager.getInstance().getDataContext(it)
      createAddInterpreterPopup(module, context)
    }
  }

  companion object {
    fun createAddInterpreterPopup(module: Module, context: DataContext): JBPopup {
      val group = DefaultActionGroup()
      group.addAll(collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { })
      ActionManager.getInstance().getAction("Python.NewInterpreter.Extra")?.let {
        group.add(it)
      }
      return JBPopupFactory.getInstance().createActionGroupPopup(
        null,
        group,
        context,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false,
      )
    }
  }
}

private class UseProvidedInterpreterFix(private val myCreateSdkInfo: CreateSdkInfoWithTool) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return ActionLink(myCreateSdkInfo.createSdkInfo.intentionName) {
      executor.execute {
        PyProjectTomlCollector.sdkCreatedFromNotification(myCreateSdkInfo.toolId)
        val lifetime = suppressTipAndInspectionsFor(module, myCreateSdkInfo.toolId.id)
        withBackgroundProgress(project, myCreateSdkInfo.createSdkInfo.intentionName, false) {
          lifetime.use { setSdkUsingCreateSdkInfo(module, myCreateSdkInfo) }
        }
        RefreshQueue.getInstance().refresh(recursive = false, files = ModuleRootManager.getInstance(module).contentRoots.toList())
      }
    }
  }
}

private class SuggestToolInstallationFix(
  private val myModule: Module,
  private val myCreateSdkInfo: CreateSdkInfo.WillInstallTool,
) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return ActionLink(myCreateSdkInfo.intentionName) {
      val pyTool = PyTool.findByPackageName(myCreateSdkInfo.toolToInstall) ?: return@ActionLink
      executor.execute {
        val lifetime = suppressTipAndInspectionsFor(myModule, myCreateSdkInfo.toolToInstall)
        withBackgroundProgress(project, myCreateSdkInfo.intentionName, false) {
          lifetime.use {
            val eel = project.getEelDescriptor().toEelApi()
            pyTool.performToolInstallation(eel).mapSuccess(myCreateSdkInfo.pathPersister).errorOrNull?.also {
              ErrorSink().emit(it, project)
            }
          }
        }
      }
    }
  }
}

private suspend fun Module.getQuickFixBySdkSuggestion(): FindQuickFixResult =
  when (val r = getModuleSdkState()) {
    is ModuleSdkState.HasSdk -> FindQuickFixResult.SdkAppliedAutomatically(r.sdk)
    is ModuleSdkState.NoSdk -> {
      r.sdkConfigInstruction?.let { instruction ->
        when (val r = instruction.autoConfigureSdkDoNotCreateFiles()) {
          is SdkConfigurationResult.ToolNotInstalled, is SdkConfigurationResult.NotConfigured -> r
          is SdkConfigurationResult.ParentHasNoSdk -> r.reason
          is SdkConfigurationResult.Configured -> return FindQuickFixResult.SdkAppliedAutomatically(r.sdk) // SDK was configured automatically (e.g. existing env attached)
        }
      }?.toQuickFix(this).let { FindQuickFixResult.ShowUserFix(it) }
    }
  }

private sealed interface FindQuickFixResult {
  /**
   * Module has no SDK.
   * We recommend user to configure SDK using [fix], or no suggestion could be made if `null` (but module still needs SDK)
   */
  class ShowUserFix(val fix: InterpreterFix?) : FindQuickFixResult

  /**
   * Module already has [sdk]
   */
  class SdkAppliedAutomatically(val sdk: Sdk) : FindQuickFixResult
}

private val logger = fileLogger()

private fun SdkConfigurationError<CreateSdkNotFilesResult>.toQuickFix(module: Module): InterpreterFix? =
  when (val r = this@toQuickFix) {
    is SdkConfigurationResult.ToolNotInstalled -> {
      logger.trace { "$this: Tool installation will be suggested to the user" }
      SuggestToolInstallationFix(module, r.tool)
    }
    is SdkConfigurationResult.NotConfigured -> {
      when (val r = r.reason) {
        is CreateSdkNotFilesResult.NoFiles -> {
          logger.trace { "$this: Ask user as it is a heavy operation" }
          UseProvidedInterpreterFix(CreateSdkInfoWithTool(r.createInfo.createSdkInfo, r.createInfo.toolId))
        }
        // TODO: We've tried to configure SDK automatically, but faced an error, what should we do?
        is CreateSdkNotFilesResult.SdkCreationError -> null
      }
    }
    // TODO: null means parent module is unconfigurable, what should we do?
    is SdkConfigurationResult.ParentHasNoSdk -> r.reason?.toQuickFix(r.parentModule)
  }


private suspend fun setSdkUsingCreateSdkInfo(module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool) {
  withContext(Dispatchers.Default) {
    logger.debug("Configuring sdk using ${createSdkInfoWithTool.toolId}")

    val sdk = when (val createSdkInfo = createSdkInfoWithTool.createSdkInfo) {
      is CreateSdkInfo.WillInstallTool ->
        // This specific CreateSdkInfo is only supposed to be used for proposing tool installation,
        // it never should be used for SDK creation.
        PyResult.localizedError(PySdkBundle.message("python.sdk.cannot.create.tool.should.be.installed"))
      is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv -> createSdkInfo.getSdkCreator(module).createSdk()
    }.getOr {
      ErrorSink().emit(it.error, module.project)
      return@withContext
    }

    module.getRootModuleOrNull(createSdkInfoWithTool.toolId)?.also { configurePythonSdk(it.project, it, sdk) }
    configurePythonSdk(module.project, module, sdk)
    logger.debug("Successfully configured sdk using ${createSdkInfoWithTool.toolId}")
  }
}