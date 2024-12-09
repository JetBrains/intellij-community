// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.InstallPackageQuickFix
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.common.normalizePackageName
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.getConfirmedPackages
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.createSpecification
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.syncWithImports
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.Requirement
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UnsatisfiedRequirementInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return UnsatisfiedRequirementInspectionVisitor(holder, isOnTheFly, session)
  }
}

private class UnsatisfiedRequirementInspectionVisitor(holder: ProblemsHolder,
                                                      onTheFly: Boolean,
                                                      session: LocalInspectionToolSession) : RequirementsInspectionVisitor(
  holder, session) {
  override fun visitRequirementsFile(element: RequirementsFile) {
    val module = ModuleUtilCore.findModuleForPsiElement(element)
    val requirementsPath = PyPackageRequirementsSettings.getInstance(module).requirementsPath
    if (!requirementsPath.isEmpty() && module != null) {
      val file = LocalFileSystem.getInstance().findFileByPath(requirementsPath)
      if (file == null) {
        val manager = ModuleRootManager.getInstance(module)
        for (root in manager.contentRoots) {
          val fileInRoot = root.findFileByRelativePath(requirementsPath)
          if (fileInRoot == null) {
            return
          }
        }
      }

      if (element.text.isNullOrBlank()) {
        holder.registerProblem(element, PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, PyGenerateRequirementsFileQuickFix(module))
      }

      val project = element.project
      val sdk = project.pythonSdk ?: return
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val packages = packageManager.installedPackages.map { normalizePackageName(it.name) }
      val unsatisfiedRequirements = element.requirements().filter { requirement -> normalizePackageName(requirement.displayName) !in packages }
      unsatisfiedRequirements.forEach { requirement ->
        holder.registerProblem(requirement,
                               PyBundle.message("INSP.requirements.package.not.installed", requirement.displayName),
                               ProblemHighlightType.WARNING,
                               InstallRequirementQuickFix(requirement),
                               InstallAllRequirementsQuickFix(unsatisfiedRequirements),
                               InstallProjectAsEditableQuickfix())
      }
    }
  }
}

class InstallAllRequirementsQuickFix(requirements: List<Requirement>) : LocalQuickFix {
  val requirements: List<SmartPsiElementPointer<Requirement>> = requirements.map { SmartPointerManager.createPointer(it) }.toList()

  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.all.requirements")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val confirmedPackages = getConfirmedPackages(requirements.mapNotNull { it.element?.displayName })
    requirements.filter { it.element?.displayName in confirmedPackages }
      .mapNotNull { it.element }
      .forEach {
        InstallRequirementQuickFix.installPackage(project, descriptor, it)
      }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}

class InstallRequirementQuickFix(requirement: Requirement) : LocalQuickFix {

  val requirement: SmartPsiElementPointer<Requirement> = SmartPointerManager.createPointer(requirement)

  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.requirement", requirement.element?.displayName ?: "")
  }

  companion object {
    private const val CONFIRM_PACKAGE_INSTALLATION_PROPERTY: String = "python.confirm.package.installation"

    fun checkAndInstall(project: Project, descriptor: ProblemDescriptor, requirement: SmartPsiElementPointer<Requirement>) {
      val req = requirement.element ?: return
      val name = req.displayName
      val isWellKnownPackage = ApplicationManager.getApplication()
        .getService(PyPIPackageRanking::class.java)
        .packageRank.containsKey(normalizePackageName(name))
      val confirmationEnabled = PropertiesComponent.getInstance().getBoolean(CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true)
      if (!isWellKnownPackage && confirmationEnabled) {
        val confirmed = yesNo(PyBundle.message("python.packaging.dialog.title.install.package.confirmation"),
                              PyBundle.message("python.packaging.dialog.message.install.package.confirmation", name))
          .icon(AllIcons.General.WarningDialog)
          .doNotAsk(ConfirmPackageInstallationDoNotAskOption())
          .ask(project)
        if (!confirmed) {
          return
        }
      }

      installPackage(project, descriptor, req)
    }

    fun installPackage(project: Project, descriptor: ProblemDescriptor, requirement: Requirement) {
      val element = descriptor.psiElement
      val file = descriptor.psiElement.containingFile ?: return
      val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
      val manager = PythonPackageManager.forSdk(project, sdk)
      val versionSpec = if (requirement is NameReq) requirement.versionspec?.text else ""
      val name = requirement.displayName

      project.service<PyPackagingToolWindowService>().serviceScope.launch(Dispatchers.IO) {
        val specification = manager.repositoryManager.createSpecification(name, versionSpec) ?: return@launch
        runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.new.project.install.failed.title", specification.name), specification.name) {
          manager.installPackage(specification, emptyList<String>())
        }
        DaemonCodeAnalyzer.getInstance(project).restart(file)
      }
    }
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    checkAndInstall(project, descriptor, requirement)
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  private class ConfirmPackageInstallationDoNotAskOption : DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.OK) {
        PropertiesComponent.getInstance().setValue(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true)
      }
    }
  }
}

class InstallProjectAsEditableQuickfix : LocalQuickFix {

  override fun getFamilyName(): String {
    return PyBundle.message("python.pyproject.install.self.as.editable")
  }

  @Suppress("DialogTitleCapitalization")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(project, sdk)
    FileDocumentManager.getInstance().saveDocument(file.virtualFile.findDocument() ?: return)

    project.service<PyPackagingToolWindowService>().serviceScope.launch {
      runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.pyproject.install.self.error"), null) {
        manager.runPackagingTool("install", listOf("-e", "."), PyBundle.message("python.pyproject.install.self.as.editable.progress"))
        manager.refreshPaths()
        runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
          manager.reloadPackages()
        }
      }
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }
}

class PyGenerateRequirementsFileQuickFix(private val myModule: Module) : LocalQuickFix {
  override fun getFamilyName(): @IntentionFamilyName String {
    return PyPsiBundle.message("QFIX.add.imported.packages.to.requirements")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    syncWithImports(myModule)
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}
