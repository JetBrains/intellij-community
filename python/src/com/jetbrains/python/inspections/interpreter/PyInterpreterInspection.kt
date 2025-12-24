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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElementVisitor
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.intellij.util.PathUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.inspections.PyAsyncFileInspectionRunner
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.ui.PyUiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

private val NAME: Pattern = Pattern.compile("Python (?<version>\\d\\.\\d+)\\s*(\\((?<name>.+?)\\))?")

class PyInterpreterInspection : PyInspection(), DumbAware {
  private val asyncFileInspectionRunner = PyAsyncFileInspectionRunner(
    PyPsiBundle.message("INSP.interpreter.checking.existing.environments")
  ) { module ->
    buildList {
      val sdkName = ProjectRootManager.getInstance(module.project).projectSdkName
      getSuitableSdkFix(sdkName, module)?.let { add(it) }
      add(ConfigureInterpreterFix())
    }
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

private suspend fun getSuitableSdkFix(name: String?, module: Module): LocalQuickFix? = withContext(Dispatchers.Default) {
  // this method is based on com.jetbrains.python.sdk.PySdkExtKt.suggestAssociatedSdkName
  // please keep it in sync with the mentioned method and com.jetbrains.python.PythonSdkConfigurator.configureSdk

  val existingSdks = getExistingSdks()

  val associatedSdk = mostPreferred(filterAssociatedSdks(module, existingSdks))
  if (associatedSdk != null) return@withContext UseExistingInterpreterFix(associatedSdk, module)

  val context = UserDataHolderBase()

  val quickFixBySdkSuggestion = module.getQuickFixBySdkSuggestion()
  if (quickFixBySdkSuggestion != null) return@withContext quickFixBySdkSuggestion

  if (name != null) {
    val matcher = NAME.matcher(name)
    if (matcher.matches()) {
      val venvName = matcher.group("name")
      if (venvName != null) {
        val detectedAssociatedViaRootNameEnv = detectAssociatedViaRootNameEnv(venvName, module, existingSdks, context)
        if (detectedAssociatedViaRootNameEnv != null) {
          return@withContext UseDetectedInterpreterFix(detectedAssociatedViaRootNameEnv, existingSdks, true, module)
        }
      }
      else {
        val detectedSystemWideSdk = detectSystemWideSdk(matcher.group("version"), module, existingSdks, context)
        if (detectedSystemWideSdk != null) {
          return@withContext UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module)
        }
      }
    }
  }

  if (PyCondaSdkCustomizer.instance.suggestSharedCondaEnvironments) {
    val sharedCondaEnv = mostPreferred(filterSharedCondaEnvs(module, existingSdks))
    if (sharedCondaEnv != null) return@withContext UseExistingInterpreterFix(sharedCondaEnv, module)
  }

  // TODO: We should use SystemPythonService here as well, postponing as it's quite unlikely we get here (although we can)
  val systemWideSdk = mostPreferred(filterSystemWideSdks(existingSdks))
  if (systemWideSdk != null) return@withContext UseExistingInterpreterFix(systemWideSdk, module)

  val fallbackCreateSdkInfo = PyCondaSdkCustomizer.instance.fallbackConfigurator?.let { configurator ->
    configurator.checkEnvironmentAndPrepareSdkCreator(module)?.let { CreateSdkInfoWithTool(it, configurator.toolId) }
  }
  if (fallbackCreateSdkInfo != null) return@withContext UseProvidedInterpreterFix(module, fallbackCreateSdkInfo)

  val detectedSystemWideSdk = detectSystemWideSdks(module, existingSdks).firstOrNull()
  if (detectedSystemWideSdk != null) return@withContext UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module)

  null
}

private fun getExistingSdks(): List<Sdk> {
  val model = ProjectSdksModel()
  model.reset(null)
  return model.sdks.filter { it.sdkType is PythonSdkType }
}

private fun detectAssociatedViaRootNameEnv(
  associatedName: String, module: Module, existingSdks: List<Sdk>, context: UserDataHolderBase,
): PyDetectedSdk? = findAssociatedViaRootNameEnv(associatedName, detectVirtualEnvs(module, existingSdks, context))

private fun detectSystemWideSdk(version: String, module: Module, existingSdks: List<Sdk>, context: UserDataHolderBase): PyDetectedSdk? {
  val parsedVersion = LanguageLevel.fromPythonVersion(version)

  return if (parsedVersion.toString() == version) {
    detectSystemWideSdks(module, existingSdks, context).firstOrNull { it.guessedLanguageLevel == parsedVersion }
  }
  else null
}

private fun findAssociatedViaRootNameEnv(associatedName: String, envs: List<PyDetectedSdk>): PyDetectedSdk? =
  envs.filter { associatedName == getVirtualEnvRootName(it) }
    .maxWithOrNull(compareBy<PyDetectedSdk> { it.guessedLanguageLevel }.thenBy { it.homePath })

private fun getVirtualEnvRootName(sdk: PyDetectedSdk): String? {
  val file = sdk.homePath?.let { PythonSdkUtil.getVirtualEnvRoot(it) } ?: return null
  return PathUtil.getFileName(file.path)
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

private abstract class UseInterpreterFix<T : Sdk>(protected val mySdk: T) : LocalQuickFix {
  @IntentionFamilyName
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter")

  @IntentionName
  override fun getName(): String = PyPsiBundle.message("INSP.interpreter.use.interpreter", PySdkPopupFactory.shortenNameInPopup(mySdk, 75))

  override fun startInWriteAction(): Boolean = false
}

private class UseExistingInterpreterFix(existingSdk: Sdk, private val myModule: Module) : UseInterpreterFix<Sdk>(existingSdk) {
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    PyUiUtil.clearFileLevelInspectionResults(descriptor.psiElement.containingFile)
    project.service<MyService>().scope.launch {
      PyProjectSdkConfiguration.setReadyToUseSdk(project, myModule, mySdk)
    }
  }
}

private class UseDetectedInterpreterFix(
  detectedSdk: PyDetectedSdk,
  private val myExistingSdks: List<Sdk>,
  private val doAssociate: Boolean,
  private val myModule: Module,
) : UseInterpreterFix<PyDetectedSdk>(detectedSdk) {
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    PyUiUtil.clearFileLevelInspectionResults(descriptor.psiElement.containingFile)
    project.service<MyService>().scope.launch {
      mySdk.setupSdk(myModule, myExistingSdks, doAssociate)
    }
  }
}

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)

private suspend fun Module.getQuickFixBySdkSuggestion(): LocalQuickFix? = when (val i = getModuleInfo()) {
  is ModuleCreateInfo.CreateSdkInfoWrapper -> {
    val tool = CreateSdkInfoWithTool(i.createSdkInfo, i.toolId)
    UseProvidedInterpreterFix(this, tool)
  }
  is ModuleCreateInfo.SameAs -> {
    val parentModuleSdk = i.parentModule.pythonSdk
    if (parentModuleSdk != null) {
      UseExistingInterpreterFix(parentModuleSdk, this)
    }
    else {
      // Parent has no SDK, configure it first
      // TODO: Check for SO
      i.parentModule.getQuickFixBySdkSuggestion()
    }
  }
  null -> null
}