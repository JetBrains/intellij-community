// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.fixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stubs.PyStubPackagesCompatibilityInspection
import com.jetbrains.python.packaging.PyRequirement

internal class IgnoreStubCompatibilityQuickFix(private val stubRequirement: PyRequirement) : ModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.stub.packages.compatibility.ignore")

  // A batch run does not apply a change of the inspection options.
  override fun availableInBatchMode(): Boolean = false

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val element = descriptor.psiElement ?: return ModCommand.nop()
    // The options of the template win, so it is the tool of the current profile, not the instance that reported the problem.
    val inspection = InspectionProjectProfileManager.getInstance(project).currentProfile
      .getUnwrappedTool(PyStubPackagesCompatibilityInspection::class.java.simpleName, element) as? PyStubPackagesCompatibilityInspection
                     ?: return ModCommand.nop()
    val name = stubRequirement.presentableText
    return ModCommand.updateInspectionOption(element, inspection) { tool ->
      if (name !in tool.ignoredStubPackages) tool.ignoredStubPackages.add(name)
    }
  }
}
