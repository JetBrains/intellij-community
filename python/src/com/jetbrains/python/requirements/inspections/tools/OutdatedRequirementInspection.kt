// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.NonModulePackageName
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsInspectionVisitor
import com.jetbrains.python.requirements.getPythonSdk
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
      val sdk = getPythonSdk(psiFile) ?: return
      val project = psiFile.project
      val packageManager = PythonPackageManager.forSdk(project, sdk)

      val outdatedPackages = packageManager.listOutdatedPackagesSnapshot().toMap()

      if (outdatedPackages.isEmpty())
        return

      val outdatedRequirements = requirements.mapNotNull { requirement ->
        val pkgName = NonModulePackageName.create(requirement.displayName, project) ?: return@mapNotNull null
        if (pkgName.name in outdatedPackages.keys)
          pkgName.name to requirement
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