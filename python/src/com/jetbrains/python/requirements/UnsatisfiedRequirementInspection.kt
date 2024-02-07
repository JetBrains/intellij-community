// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.createSpecification
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.Requirement
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UnsatisfiedRequirementInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return RequirementsUnresolvedRequirementInspectionVisitor(holder, isOnTheFly, session)
  }
}

private class RequirementsUnresolvedRequirementInspectionVisitor(holder: ProblemsHolder,
                                                                 onTheFly: Boolean,
                                                                 session: LocalInspectionToolSession) : RequirementsInspectionVisitor(
  holder, onTheFly, session) {
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

      val project = element.project
      val sdk = project.pythonSdk ?: return
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val packages = packageManager.installedPackages.map { it.name }
      val unsatisfiedRequirements = element.requirements().filter { requirement -> requirement.displayName !in packages }
      unsatisfiedRequirements.forEach { requirement ->
        holder.registerProblem(requirement,
                               PyBundle.message("INSP.requirements.package.requirements.not.satisfied", requirement.displayName),
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
    val element = descriptor.psiElement
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(project, sdk)

    requirements.forEach {
      val req = it.element ?: return@forEach
      val versionSpec = if (req is NameReq) req.versionspec?.text else ""
      val name = req.displayName
      project.service<PyPackagingToolWindowService>().serviceScope.launch(Dispatchers.IO) {
        val specification = manager.repositoryManager.createSpecification(name, versionSpec)
                            ?: return@launch
        manager.installPackage(specification)
        DaemonCodeAnalyzer.getInstance(project).restart(file)
      }
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

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(project, sdk)
    val req = requirement.element ?: return

    val versionSpec = if (req is NameReq) req.versionspec?.text else ""
    val name = req.displayName
    project.service<PyPackagingToolWindowService>().serviceScope.launch(Dispatchers.IO) {
      manager.installPackage(manager.repositoryManager.createSpecification(name, versionSpec) ?: return@launch)
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean {
    return false
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
        manager.reloadPackages()
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