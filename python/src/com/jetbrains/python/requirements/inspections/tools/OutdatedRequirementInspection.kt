// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsInspectionVisitor
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.quickfixes.PyGenerateRequirementsFileQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateAllRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateRequirementQuickFix

internal class OutdatedRequirementInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ) = object : RequirementsInspectionVisitor(holder, session) {
    override fun visitRequirementsFile(requirementsFile: RequirementsFile) {
      val requirements = requirementsFile.requirements()

      val psiFile = session.file
      if (psiFile.text.isNullOrBlank()) {
        val fixes = ModuleUtilCore.findModuleForPsiElement(psiFile)?.let { module ->
          arrayOf(PyGenerateRequirementsFileQuickFix(module))
        } ?: emptyArray()
        holder.registerProblem(psiFile, PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)
        return
      }

      val sdk = getPythonSdk(psiFile) ?: return

      val packageManager = PythonPackageManager.Companion.forSdk(psiFile.project, sdk)

      val outdatedPackages = packageManager.outdatedPackages.toMap()
      if (outdatedPackages.isEmpty())
        return

      val outdatedRequirements = requirements.mapNotNull { requirement ->
        val packageName = normalizePackageName(requirement.displayName)
        if (packageName in outdatedPackages.keys)
          packageName to requirement
        else
          null
      }.toMap()


      val updateAllRequirementQuickFix = UpdateAllRequirementQuickFix(outdatedRequirements.keys)
        .takeIf { outdatedRequirements.size > 1 }
      outdatedRequirements.forEach { (name, requirement) ->
        val outdatedPackage = outdatedPackages[name] ?: return@forEach
        val description = PyBundle.message(
          "python.sdk.inspection.message.version.outdated.latest",
          requirement.displayName,
          outdatedPackage.version,
          outdatedPackage.latestVersion
        )
        val fixes = listOfNotNull(
          updateAllRequirementQuickFix,
          UpdateRequirementQuickFix(name)
        ).toTypedArray()
        holder.registerProblem(requirement, description,
                               ProblemHighlightType.WEAK_WARNING, *fixes)
      }

    }
  }

  override fun isDumbAware(): Boolean = true
}