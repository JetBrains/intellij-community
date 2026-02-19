// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElementVisitor
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.Result
import com.jetbrains.python.inspections.InspectionRunnerResult
import com.jetbrains.python.inspections.PyAsyncFileInspectionRunner
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import com.jetbrains.python.sdk.configuration.createSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.ui.PyUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PyInterpreterInspection : PyInspection(), DumbAware {
  private val asyncFileInspectionRunner = PyAsyncFileInspectionRunner(
    PyPsiBundle.message("INSP.interpreter.checking.existing.environments")
  ) { module ->
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

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    PyInterpreterInspectionVisitor(holder, PyInspectionVisitor.getContext(session), asyncFileInspectionRunner)
}

private class PyInterpreterInspectionVisitor(
  holder: ProblemsHolder?,
  context: TypeEvalContext,
  private val asyncFileInspectionRunner: PyAsyncFileInspectionRunner,
) : PyInspectionVisitor(holder, context) {

  override fun visitPyFile(node: PyFile) {
    if (isFileIgnored(node)) return

    val module = ModuleUtilCore.findModuleForPsiElement(node)
    val sdk = PyBuiltinCache.findSdkForFile(node)
    val pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde()

    if (sdk == null) {
      val message = if (pyCharm) {
        PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.project")
      }
      else {
        PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.module")
      }
      registerProblemWithCommonFixes(node, message, module, pyCharm)
    }
  }

  private fun registerProblemWithCommonFixes(node: PyFile, @InspectionMessage message: String, module: Module?, pyCharm: Boolean) {
    if (module != null && pyCharm) {
      asyncFileInspectionRunner.runInspection(node, module)?.let { fixes ->
        registerProblem(node, message, *fixes.toTypedArray())
      }
    }
    else {
      registerProblem(node, message, InterpreterSettingsQuickFix(module))
    }
  }
}

private suspend fun getSuitableSdkFix(
  module: Module, moduleCreateInfo: ModuleCreateInfo?,
): LocalQuickFix? = withContext(Dispatchers.Default) {
  when (val r = module.getQuickFixBySdkSuggestion(moduleCreateInfo)) {
    is FindQuickFixResult.ShowUserFix -> r.fix
    else -> null
  }
}


private fun isFileIgnored(pyFile: PyFile): Boolean =
  PyInspectionExtension.EP_NAME.extensionList.any { it.ignoreInterpreterWarnings(pyFile) }


private class ConfigureInterpreterFix : LocalQuickFix {
  @IntentionFamilyName
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.interpreter.configure.python.interpreter")

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    PySdkPopupFactory.createAndShow(module)
  }
}

private class UseProvidedInterpreterFix(private val myModule: Module, private val myCreateSdkInfo: CreateSdkInfoWithTool) : LocalQuickFix {
  @IntentionFamilyName
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter")

  @IntentionName
  override fun getName(): String = myCreateSdkInfo.createSdkInfo.intentionName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    PyProjectSdkConfiguration.configureSdkUsingCreateSdkInfo(myModule, myCreateSdkInfo)
    PyUiUtil.clearFileLevelInspectionResults(descriptor.psiElement.containingFile)
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    // The quick fix doesn't change the code and is suggested on a file level
    return IntentionPreviewInfo.EMPTY
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
  /**
   * Show this fix to user
   */
  class ShowUserFix(val fix: LocalQuickFix) : FindQuickFixResult

  /**
   * Fix applied automatically, sdk is [sdk]
   */
  class SdkAppliedAutomatically(val sdk: Sdk) : FindQuickFixResult

  /**
   * Couldn't find any usable SDK for module
   */
  data object NoSuggestion : FindQuickFixResult
}

private val logger = fileLogger()