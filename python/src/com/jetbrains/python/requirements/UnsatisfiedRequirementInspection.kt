// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.pythonSdk

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
      element.requirements().forEach { requirement ->
        if (requirement.displayName !in packages) {
          holder.registerProblem(requirement,
                                 PyBundle.message("INSP.requirements.package.requirements.not.satisfied", requirement.displayName),
                                 ProblemHighlightType.WARNING)
        }
      }
    }
  }
}
