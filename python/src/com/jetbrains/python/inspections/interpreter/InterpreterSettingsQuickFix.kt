// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.Result
import com.jetbrains.python.inspections.InspectionRunnerResult
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.intellij.openapi.util.use
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import com.jetbrains.python.sdk.configuration.createSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.switchToSdk
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Executor that accepts at most one concurrent task.
 * While a task is running, subsequent submissions are silently discarded.
 *
 * [isBusy] reflects the current state and can be used to update UI accordingly
 * (e.g., replacing action links with a progress indicator).
 */
@ApiStatus.Internal
interface BusyGuardExecutor {
  val isBusy: Boolean
  fun execute(action: suspend () -> Unit)
}

/**
 * Provides an [ActionLink] for the "no Python interpreter" editor notification banner.
 *
 * Implementations are discovered asynchronously by [PyAsyncFileInspectionRunner][com.jetbrains.python.inspections.PyAsyncFileInspectionRunner]
 * and rendered inside [PyInterpreterNotificationProvider].
 * Long-running work (SDK creation, tool installation) must be submitted through the supplied [BusyGuardExecutor]
 * so that all notification panels share the same busy state.
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
  val moduleCreateInfo = module.getModuleInfo()
  val fixes = buildList {
    getSuitableSdkFix(module, moduleCreateInfo)?.let { add(it) }
    add(ConfigureInterpreterFix())
  }
  val shouldCache = when (moduleCreateInfo) {
    is ModuleCreateInfo.SameAs -> false
    is ModuleCreateInfo.CreateSdkInfoWrapper, null -> true
  }
  InspectionRunnerResult(fixes, shouldCache)
}

private suspend fun getSuitableSdkFix(
  module: Module, moduleCreateInfo: ModuleCreateInfo?,
): InterpreterFix? = withContext(Dispatchers.Default) {
  when (val r = module.getQuickFixBySdkSuggestion(moduleCreateInfo)) {
    is FindQuickFixResult.ShowUserFix -> r.fix
    else -> null
  }
}

internal class ConfigureInterpreterFix : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return DropDownLink(PyBundle.message("python.sdk.custom.environment")) {
      val context = DataManager.getInstance().getDataContext(it)
      createAddInterpreterPopup(module, context, executor)
    }
  }

  companion object {
    fun createAddInterpreterPopup(module: Module, context: DataContext, executor: BusyGuardExecutor): JBPopup {
      val currentSdk = module.pythonSdk
      val group = DefaultActionGroup()
      group.addAll(collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { sdk ->
        executor.execute {
          withContext(Dispatchers.IO) { switchToSdk(module, sdk, currentSdk) }
        }
      })
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

private class UseProvidedInterpreterFix(private val myModule: Module, private val myCreateSdkInfo: CreateSdkInfoWithTool) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return ActionLink(myCreateSdkInfo.createSdkInfo.intentionName) {
      executor.execute {
        val lifetime = PyProjectSdkConfiguration.suppressTipAndInspectionsFor(myModule, myCreateSdkInfo.toolId.id)
        withBackgroundProgress(project, myCreateSdkInfo.createSdkInfo.intentionName, false) {
          lifetime.use { PyProjectSdkConfiguration.setSdkUsingCreateSdkInfo(myModule, myCreateSdkInfo) }
        }
        RefreshQueue.getInstance().refresh(recursive = false, files = ModuleRootManager.getInstance(myModule).contentRoots.toList())
      }
    }
  }
}

private class SuggestToolInstallationFix(
  private val myModule: Module,
  private val myCreateSdkInfo: CreateSdkInfo.WillInstallTool,
  private val myTool: ToolId,
) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return ActionLink(myCreateSdkInfo.intentionName) {
      executor.execute {
        val lifetime = PyProjectSdkConfiguration.suppressTipAndInspectionsFor(myModule, myTool.id)
        withBackgroundProgress(project, myCreateSdkInfo.intentionName, false) {
          lifetime.use { PyProjectSdkConfiguration.installToolAndShowErrorIfNeeded(myModule, myCreateSdkInfo.pathPersister, myCreateSdkInfo.toolToInstall) }
        }
      }
    }
  }
}

private suspend fun Module.getQuickFixBySdkSuggestion(i: ModuleCreateInfo?): FindQuickFixResult = when (i) {
  is ModuleCreateInfo.CreateSdkInfoWrapper -> {
    when (val createSdkInfo = i.createSdkInfo) {
      is CreateSdkInfo.ExistingEnv -> {
        logger.trace { "$this: Files already exist, just create sn SDK" }
        when (val creationResult = createSdkInfo.createSdk(module = this)) {
          is Result.Failure -> {
            logger.warn("Can't create SDK for $this : ${creationResult.error}")
            FindQuickFixResult.NoSuggestion
          }
          is Result.Success -> {
            val sdk = creationResult.result
            logger.trace { "$this: sdk $sdk created" }
            pythonSdk = sdk // SDK can't be null
            project.pySdkService.persistSdk(sdk)
            sdk.setAssociationToModule(this)
            PyProjectTomlCollector.sdkCreatedAutomatically(sdk, i.toolId)
            FindQuickFixResult.SdkAppliedAutomatically(sdk)
          }
        }
      }
      is CreateSdkInfo.WillCreateEnv -> {
        logger.trace { "$this: Ask user as it is a heavy operation" }
        val tool = CreateSdkInfoWithTool(createSdkInfo, i.toolId)
        FindQuickFixResult.ShowUserFix(UseProvidedInterpreterFix(this, tool))
      }
      is CreateSdkInfo.WillInstallTool -> {
        logger.trace { "$this: Tool installation will be suggested to the user" }
        FindQuickFixResult.ShowUserFix(SuggestToolInstallationFix(this, createSdkInfo, i.toolId))
      }
    }
  }
  is ModuleCreateInfo.SameAs -> {
    logger.trace { "$this: Same as parent" }
    i.parentModule.pythonSdk?.let { parentModuleSdk ->
      logger.trace { "$this: Parent has SDK $parentModuleSdk" }
      pythonSdk = parentModuleSdk
      FindQuickFixResult.SdkAppliedAutomatically(parentModuleSdk)
    } ?:
    // Try to find SDK for parent otherwise
    when (val parentResult = i.parentModule.getQuickFixBySdkSuggestion(i.parentModule.getModuleInfo())) {
      is FindQuickFixResult.SdkAppliedAutomatically -> {
        val parentModuleSdk = parentResult.sdk
        logger.trace { "$this: Parent has SDK $parentModuleSdk" }
        pythonSdk = parentModuleSdk
        FindQuickFixResult.SdkAppliedAutomatically(parentModuleSdk)
      }
      FindQuickFixResult.NoSuggestion, is FindQuickFixResult.ShowUserFix -> {
        logger.trace { "$this: Parent SDK can't be created ($parentResult), so is ours" }
        parentResult
      }
    }
  }
  null -> FindQuickFixResult.NoSuggestion
}

private sealed interface FindQuickFixResult {
  class ShowUserFix(val fix: InterpreterFix) : FindQuickFixResult
  class SdkAppliedAutomatically(val sdk: Sdk) : FindQuickFixResult
  data object NoSuggestion : FindQuickFixResult
}

private val logger = fileLogger()
