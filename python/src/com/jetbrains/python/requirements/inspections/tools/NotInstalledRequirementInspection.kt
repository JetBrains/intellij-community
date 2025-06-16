// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsInspectionVisitor
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.quickfixes.InstallAllRequirementsQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.InstallRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.PyGenerateRequirementsFileQuickFix

class NotInstalledRequirementInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : RequirementsInspectionVisitor(holder, session) {
    override fun visitRequirementsFile(requirementsFile: RequirementsFile) {
      val requirements = requirementsFile.requirements().toList()
      val psiFile = session.file
      val sdk = getPythonSdk(psiFile) ?: return

      if (psiFile.text.isNullOrBlank()) {
        val fixes = ModuleUtilCore.findModuleForPsiElement(psiFile)?.let { module ->
          arrayOf(PyGenerateRequirementsFileQuickFix(module))
        } ?: emptyArray()
        holder.registerProblem(psiFile, PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)
        return
      }

      val packageManager = PythonPackageManager.forSdk(psiFile.project, sdk)

      val packages = packageManager.listInstalledPackagesSnapshot()
      val pyPackages = packages.map { PyPackage(it.name, it.version) }


      val notInstalledRequirementsWithPsi = requirements.mapNotNull { requirement ->
        val pyRequirement = PyRequirementParser.fromLine(requirement.text) ?: return@mapNotNull null
        val isMatched = pyRequirement.match(pyPackages) != null
        if (isMatched)
          return@mapNotNull null
        requirement to pyRequirement
      }
      val unsatisfiedRequirements = notInstalledRequirementsWithPsi.map { it.second }


      val installAllRequirementsQuickFix = InstallAllRequirementsQuickFix(unsatisfiedRequirements).takeIf { unsatisfiedRequirements.size > 1 }
      notInstalledRequirementsWithPsi.forEach { (psiRequirement, pyRequirement) ->
        val fixes = listOfNotNull(
          InstallRequirementQuickFix(pyRequirement),
          installAllRequirementsQuickFix,
        )
        holder.registerProblem(psiRequirement, PyBundle.message("INSP.requirements.package.not.installed", psiRequirement.requirement),
                               ProblemHighlightType.WARNING,
                               *fixes.toTypedArray())
      }
    }
  }

  override fun isDumbAware(): Boolean = true
}