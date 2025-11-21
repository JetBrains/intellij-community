// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.actions.ShowSettingsUtilImpl.Companion.showSettingsDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.findSdkForFile
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.createAndShow
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer.Companion.checkEnvironmentAndPrepareSdkCreatorBlocking
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer.Companion.instance
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.configureSdkUsingCreateSdkInfo
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.ui.PyUiUtil
import one.util.streamex.StreamEx
import java.io.File
import java.util.function.Function
import java.util.regex.Matcher
import java.util.regex.Pattern

class PyInterpreterInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  class Visitor(
    holder: ProblemsHolder?,
    context: TypeEvalContext
  ) : PyInspectionVisitor(holder, context) {
    override fun visitPyFile(node: PyFile) {
      if (isFileIgnored(node)) return
      val module = ModuleUtilCore.findModuleForPsiElement(node)
      val sdk = findSdkForFile(node)
      val pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde()

      val fixes: MutableList<LocalQuickFix?> = ArrayList<LocalQuickFix?>()
      if (sdk == null) {
        val message: @InspectionMessage String
        if (pyCharm) {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.project")
        }
        else {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.module")
        }
        registerProblemWithCommonFixes(node, message, module, fixes, pyCharm)
      }
    }


    private fun registerProblemWithCommonFixes(
      node: PyFile,
      @InspectionMessage message: @InspectionMessage String,
      module: Module?,
      fixes: MutableList<LocalQuickFix?>,
      pyCharm: Boolean
    ) {
      if (module != null && pyCharm) {
        val sdkName = ProjectRootManager.getInstance(node.getProject()).getProjectSdkName()
        ContainerUtil.addIfNotNull<LocalQuickFix?>(fixes, getSuitableSdkFix(sdkName, module))
      }
      if (module != null && pyCharm) {
        fixes.add(ConfigureInterpreterFix())
      }
      else {
        fixes.add(InterpreterSettingsQuickFix(module))
      }

      registerProblem(node, message, *fixes.toArray<LocalQuickFix?>(LocalQuickFix.EMPTY_ARRAY))
    }

    companion object {
      private fun getSuitableSdkFix(name: String?, module: Module): LocalQuickFix? {
        // this method is based on com.jetbrains.python.sdk.PySdkExtKt.suggestAssociatedSdkName
        // please keep it in sync with the mentioned method and com.jetbrains.python.PythonSdkConfigurator.configureSdk

        val existingSdks: List<Sdk> = existingSdks

        val associatedSdk = mostPreferred(filterAssociatedSdks(module, existingSdks))
        if (associatedSdk != null) return UseExistingInterpreterFix(associatedSdk, module)

        val context = UserDataHolderBase()

        val createSdkInfos: List<CreateSdkInfoWithTool> = findAllSortedForModuleForJvm(module)
        if (!createSdkInfos.isEmpty()) {
          return UseProvidedInterpreterFix(module, createSdkInfos.first())
        }

        if (name != null) {
          val matcher: Matcher = NAME.matcher(name)
          if (matcher.matches()) {
            val venvName = matcher.group("name")
            if (venvName != null) {
              val detectedAssociatedViaRootNameEnv: PyDetectedSdk? = detectAssociatedViaRootNameEnv(venvName, module, existingSdks, context)
              if (detectedAssociatedViaRootNameEnv != null) {
                return UseDetectedInterpreterFix(detectedAssociatedViaRootNameEnv, existingSdks, true, module)
              }
            }
            else {
              val detectedSystemWideSdk: PyDetectedSdk? = detectSystemWideSdk(matcher.group("version"), module, existingSdks, context)
              if (detectedSystemWideSdk != null) {
                return UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module)
              }
            }
          }
        }

        if (instance.suggestSharedCondaEnvironments) {
          val sharedCondaEnv = mostPreferred(filterSharedCondaEnvs(module, existingSdks))
          if (sharedCondaEnv != null) {
            return UseExistingInterpreterFix(sharedCondaEnv, module)
          }
        }

        // TODO: We should use SystemPythonService here as well, postponing as it's quite unlikely we get here (although we can)
        val systemWideSdk = mostPreferred(filterSystemWideSdks(existingSdks))
        if (systemWideSdk != null) {
          return UseExistingInterpreterFix(systemWideSdk, module)
        }

        val configurator = instance.fallbackConfigurator
        if (configurator != null) {
          val fallbackCreateSdkInfo =
            checkEnvironmentAndPrepareSdkCreatorBlocking(configurator, module)
          if (fallbackCreateSdkInfo != null) {
            return UseProvidedInterpreterFix(module, fallbackCreateSdkInfo)
          }
        }

        val detectedSystemWideSdk = ContainerUtil.getFirstItem<PyDetectedSdk>(detectSystemWideSdks(module, existingSdks))
        if (detectedSystemWideSdk != null) {
          return UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module)
        }

        return null
      }

      private val existingSdks: List<Sdk>
        get() {
          val model = ProjectSdksModel()
          model.reset(null)
          return ContainerUtil.filter(model.getSdks(),
                                            Condition { it.getSdkType() is PythonSdkType })
        }

      private fun detectAssociatedViaRootNameEnv(
        associatedName: String,
        module: Module,
        existingSdks: List<Sdk>,
        context: UserDataHolderBase
      ): PyDetectedSdk? {
        return Companion.findAssociatedViaRootNameEnv(
          associatedName,
          detectVirtualEnvs(module, existingSdks, context),
          Function { sdk: PyDetectedSdk? -> Companion.getVirtualEnvRootName(sdk!!) }
        )
      }

      private fun detectSystemWideSdk(
        version: String,
        module: Module,
        existingSdks: List<Sdk>,
        context: UserDataHolderBase
      ): PyDetectedSdk? {
        val parsedVersion: LanguageLevel = LanguageLevel.fromPythonVersion(version)!!

        if (parsedVersion.toString() == version) {
          return ContainerUtil.find<PyDetectedSdk>(
            detectSystemWideSdks(module, existingSdks, context),
            Condition { sdk: PyDetectedSdk -> sdk.guessedLanguageLevel == parsedVersion }
          )
        }

        return null
      }

      private fun findAssociatedViaRootNameEnv(
        associatedName: String,
        envs: List<PyDetectedSdk>,
        envRootName: Function<PyDetectedSdk?, String?>
      ): PyDetectedSdk? {
        return StreamEx
          .of(envs)
          .filter { sdk: PyDetectedSdk -> associatedName == envRootName.apply(sdk) }
          .max(compareBy<PyDetectedSdk> { it.guessedLanguageLevel }.thenBy { it.homePath })
          .orElse(null)
      }

      private fun getVirtualEnvRootName(sdk: PyDetectedSdk): String? {
        val path = sdk.getHomePath()
        return if (path == null) null else getEnvRootName(PythonSdkUtil.getVirtualEnvRoot(path))
      }

      private fun getEnvRootName(envRoot: File?): String? {
        return if (envRoot == null) null else PathUtil.getFileName(envRoot.getPath())
      }
    }
  }

  class InterpreterSettingsQuickFix(private val myModule: Module?) : LocalQuickFix {
    override fun getFamilyName(): String {
      return if (PlatformUtils.isPyCharm())
        PyPsiBundle.message("INSP.interpreter.interpreter.settings")
      else
        PyPsiBundle.message("INSP.interpreter.configure.python.interpreter")
    }

    override fun startInWriteAction(): Boolean {
      return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      showPythonInterpreterSettings(project, myModule)
    }

    companion object {
      fun showPythonInterpreterSettings(project: Project, module: Module?) {
        val id = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable"
        val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)
        if (ConfigurableVisitor.findById(id, mutableListOf<ConfigurableGroup?>(group)) != null) {
          showSettingsDialog(project, id, null)
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
        return ProjectRootManager.getInstance(project).getProjectSdk() == null &&
               ModuleRootManager.getInstance(module).isSdkInherited() && getInstance(project).modules.size < 2
      }
    }
  }

  class ConfigureInterpreterFix : LocalQuickFix {
    @IntentionFamilyName
    override fun getFamilyName(): @IntentionFamilyName String {
      return PyPsiBundle.message("INSP.interpreter.configure.python.interpreter")
    }

    override fun startInWriteAction(): Boolean {
      return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.getPsiElement()
      if (element == null) return

      val module = ModuleUtilCore.findModuleForPsiElement(element)
      if (module == null) return

      createAndShow(module)
    }
  }

  private class UseProvidedInterpreterFix(
    private val myModule: Module,
    private val myCreateSdkInfo: CreateSdkInfoWithTool
  ) : LocalQuickFix {
    @IntentionFamilyName
    override fun getFamilyName(): @IntentionFamilyName String {
      return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter")
    }

    @IntentionName
    override fun getName(): @IntentionName String {
      return myCreateSdkInfo.createSdkInfo.intentionName
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      if (!detectSdkForModulesForJvmIn(project)) {
        configureSdkUsingCreateSdkInfo(myModule, myCreateSdkInfo)
      }
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      // The quick fix doesn't change the code and is suggested on a file level
      return IntentionPreviewInfo.EMPTY
    }
  }

  private abstract class UseInterpreterFix<T : Sdk> protected constructor(protected val mySdk: T) : LocalQuickFix {
    @IntentionFamilyName
    override fun getFamilyName(): @IntentionFamilyName String {
      return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter")
    }

    @IntentionName
    override fun getName(): @IntentionName String {
      return PyPsiBundle.message("INSP.interpreter.use.interpreter", PySdkPopupFactory.Companion.shortenNameInPopup(mySdk!!, 75))
    }

    override fun startInWriteAction(): Boolean {
      return false
    }
  }

  private class UseExistingInterpreterFix(existingSdk: Sdk, private val myModule: Module) : UseInterpreterFix<Sdk>(existingSdk) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      PyUiUtil.clearFileLevelInspectionResults(descriptor.getPsiElement().getContainingFile())
      ApplicationManager.getApplication().executeOnPooledThread(
        Runnable {
          PyProjectSdkConfiguration.setReadyToUseSdkSync(project, myModule, mySdk)
        })
    }
  }

  private class UseDetectedInterpreterFix(
    detectedSdk: PyDetectedSdk,
    private val myExistingSdks: List<Sdk>,
    private val doAssociate: Boolean,
    private val myModule: Module
  ) : UseInterpreterFix<PyDetectedSdk>(detectedSdk) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      PyUiUtil.clearFileLevelInspectionResults(descriptor.getPsiElement().getContainingFile())
      mySdk.setupSdkLaunch(myModule, myExistingSdks, doAssociate)
    }
  }

  companion object {
    private val NAME: Pattern = Pattern.compile("Python (?<version>\\d\\.\\d+)\\s*(\\((?<name>.+?)\\))?")

    private fun isFileIgnored(pyFile: PyFile): Boolean {
      return PyInspectionExtension.EP_NAME.extensionList.stream().anyMatch { ep: PyInspectionExtension? ->
        ep!!.ignoreInterpreterWarnings(pyFile)
      }
    }
  }
}
