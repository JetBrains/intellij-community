// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkType
import javax.swing.JComponent

class PyStubPackagesCompatibilityInspection : PyInspection() {

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoredStubPackages: MutableList<String> = mutableListOf()

  override fun createOptionsPanel(): JComponent = ListEditForm("Ignored stub packages", ignoredStubPackages).contentPanel

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(ignoredStubPackages, holder, session)
  }

  private class Visitor(val ignoredStubPackages: MutableList<String>,
                        holder: ProblemsHolder,
                        session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyFile(node: PyFile) {
      val module = ModuleUtilCore.findModuleForFile(node) ?: return
      val sdk = PythonSdkType.findPythonSdk(module) ?: return

      val installedPackages = PyPackageManager.getInstance(sdk).packages ?: emptyList()
      if (installedPackages.isEmpty()) return

      val nameToPkg = mutableMapOf<String, PyPackage>()
      installedPackages.forEach { nameToPkg[it.name] = it }

      installedPackages
        .asSequence()
        .filter { pkg -> pkg.name.let { it.endsWith(STUBS_SUFFIX) && it !in ignoredStubPackages } }
        .mapNotNull { stubPkg -> nameToPkg[stubPkg.name.removeSuffix(STUBS_SUFFIX)]?.let { it to stubPkg } }
        .forEach { (runtimePkg, stubPkg) ->
          val runtimePkgName = runtimePkg.name
          val requirement = stubPkg.requirements.firstOrNull { it.name == runtimePkgName } ?: return@forEach

          if (requirement.match(listOf(runtimePkg)) == null) {
            val stubPkgName = stubPkg.name
            val specsToString = StringUtil.join(requirement.versionSpecs, { it.presentableText }, ", ")

            registerProblem(node,
                            "'$stubPkgName${PyRequirementRelation.EQ.presentableText}${stubPkg.version}' " +
                            "is incompatible with " +
                            "'$runtimePkgName${PyRequirementRelation.EQ.presentableText}${runtimePkg.version}'. " +
                            "Expected '$runtimePkgName' version: [$specsToString]",
                            PyInterpreterInspection.ConfigureInterpreterFix(),
                            createIgnoreStubPackageQuickFix(stubPkgName, ignoredStubPackages))
          }
        }
    }

    private fun createIgnoreStubPackageQuickFix(stubPkgName: String, ignoredStubPkgs: MutableList<String>): LocalQuickFix {
      return object : LocalQuickFix {
        override fun getFamilyName() = "Ignore '$stubPkgName' compatibility"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
          if (ignoredStubPkgs.add(stubPkgName)) ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
        }
      }
    }
  }
}