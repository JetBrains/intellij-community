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
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.autoConfigureSdkIfNeeded
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.inspections.InspectionRunnerResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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
  val moduleCreateInfo = module.getModuleInfo()
  val fixes = buildList {
    getSuitableSdkFix(module, moduleCreateInfo)?.let { add(it) }
    moduleCreateInfo?.let { add(ConfigureInterpreterFix()) }
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

private class UseProvidedInterpreterFix(
  private val myCreateSdkInfo: CreateSdkInfoWithTool,
  private val modulePath: Path?,
) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    return ActionLink(myCreateSdkInfo.createSdkInfo.intentionName) {
      executor.execute {
        PyProjectTomlCollector.sdkCreatedFromNotification(myCreateSdkInfo.toolId)
        val lifetime = PyProjectSdkConfiguration.suppressTipAndInspectionsFor(module, myCreateSdkInfo.toolId.id)
        withBackgroundProgress(project, myCreateSdkInfo.createSdkInfo.intentionName, false) {
          lifetime.use { PyProjectSdkConfiguration.setSdkUsingCreateSdkInfo(module, myCreateSdkInfo) }
        }
        RefreshQueue.getInstance().refresh(recursive = false, files = ModuleRootManager.getInstance(module).contentRoots.toList())
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

private suspend fun Module.getQuickFixBySdkSuggestion(i: ModuleCreateInfo?): FindQuickFixResult {
  // Try auto-configure (waits for SDK table, handles ExistingEnv and SameAs)
  autoConfigureSdkIfNeeded()?.orLogException(logger)?.let { return FindQuickFixResult.SdkAppliedAutomatically(it) }

  // No existing env — show user fix for WillCreateEnv / WillInstallTool
  return when (i) {
    is ModuleCreateInfo.CreateSdkInfoWrapper -> {
      when (val createSdkInfo = i.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv -> FindQuickFixResult.NoSuggestion // already handled by autoConfigureSdkIfNeeded
        is CreateSdkInfo.WillCreateEnv -> {
          logger.trace { "$this: Ask user as it is a heavy operation" }
          FindQuickFixResult.ShowUserFix(UseProvidedInterpreterFix(CreateSdkInfoWithTool(createSdkInfo, i.toolId), i.moduleDir))
        }
        is CreateSdkInfo.WillInstallTool -> {
          logger.trace { "$this: Tool installation will be suggested to the user" }
          FindQuickFixResult.ShowUserFix(SuggestToolInstallationFix(this, createSdkInfo, i.toolId))
        }
      }
    }
    is ModuleCreateInfo.SameAs, null -> FindQuickFixResult.NoSuggestion // SameAs already handled by autoConfigureSdkIfNeeded
  }
}

private sealed interface FindQuickFixResult {
  class ShowUserFix(val fix: InterpreterFix) : FindQuickFixResult
  class SdkAppliedAutomatically(val sdk: Sdk) : FindQuickFixResult
  data object NoSuggestion : FindQuickFixResult
}

private val logger = fileLogger()
