// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.outdated

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsInspectionVisitor
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.outdated.quickfixes.UpdateAllRequirementQuickFix
import com.jetbrains.python.requirements.inspections.outdated.quickfixes.UpdateRequirementQuickFix

internal class OutdatedRequirementInspectionVisitor(
  holder: ProblemsHolder,
  session: LocalInspectionToolSession,
) : RequirementsInspectionVisitor(holder, session) {

  override fun visitRequirementsFile(element: RequirementsFile) {
    if (element.text.isNullOrBlank()) {
      return
    }

    processOutdatedPackages(element)
  }

  private fun processOutdatedPackages(element: RequirementsFile) {
    val sdk = getPythonSdk(element) ?: return
    val packageManager = PythonPackageManager.forSdk(element.project, sdk)

    val outdatedPackages = packageManager.outdatedPackages
    if (outdatedPackages.isEmpty())
      return

    val outdatedRequirements = element.requirements().mapNotNull { requirement ->
      val packageName = normalizePackageName(requirement.displayName)
      if (packageName in outdatedPackages.keys)
        packageName to requirement
      else
        null
    }.toMap()

    outdatedRequirements.forEach { (name, requirement) ->
      val outdatedPackage = outdatedPackages[name] ?: return@forEach
      val description = PyBundle.message(
        "python.sdk.inspection.message.version.outdated.latest",
        requirement.displayName,
        outdatedPackage.version,
        outdatedPackage.latestVersion
      )
      val fixes = arrayOf(
        UpdateAllRequirementQuickFix(outdatedRequirements.keys),
        UpdateRequirementQuickFix(name)
      )
      holder.registerProblem(requirement, description, *fixes)
    }
  }
}