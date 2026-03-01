// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.actions.ShowSettingsUtilImpl
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
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.intellij.psi.PsiFile
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.Result
import com.jetbrains.python.inspections.InspectionRunnerResult
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import com.jetbrains.python.sdk.configuration.createSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface InterpreterFix {
  val name: @Nls String
  fun apply(module: Module, project: Project, psiFile: PsiFile)
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

private class ConfigureInterpreterFix : InterpreterFix {
  override val name: String = PyBundle.message("python.sdk.configure.python.interpreter")

  override fun apply(module: Module, project: Project, psiFile: PsiFile) {
    PySdkPopupFactory.createAndShow(module)
  }
}

private class UseProvidedInterpreterFix(private val myModule: Module, private val myCreateSdkInfo: CreateSdkInfoWithTool) : InterpreterFix {
  override val name: String = myCreateSdkInfo.createSdkInfo.intentionName

  override fun apply(module: Module, project: Project, psiFile: PsiFile) {
    PyProjectSdkConfiguration.configureSdkUsingCreateSdkInfo(myModule, myCreateSdkInfo)
  }
}

private class SuggestToolInstallationFix(
  private val myModule: Module,
  private val myCreateSdkInfo: CreateSdkInfo.WillInstallTool,
  private val myTool: ToolId,
) : InterpreterFix {
  override val name: String = myCreateSdkInfo.intentionName

  override fun apply(module: Module, project: Project, psiFile: PsiFile) {
    PyProjectSdkConfiguration.installToolForInspection(myModule, myCreateSdkInfo, myTool)
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
