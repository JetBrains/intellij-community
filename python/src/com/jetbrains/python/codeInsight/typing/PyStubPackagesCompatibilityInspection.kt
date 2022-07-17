// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil
import javax.swing.JComponent

class PyStubPackagesCompatibilityInspection : PyInspection() {

  companion object {
    fun findIncompatibleRuntimeToStubPackages(sdk: Sdk,
                                              stubPkgsFilter: (PyPackage) -> Boolean): List<Pair<PyPackage, PyPackage>> {
      val installedPackages = PyPackageManager.getInstance(sdk).packages ?: return emptyList()
      if (installedPackages.isEmpty()) return emptyList()

      val nameToPkg = mutableMapOf<String, PyPackage>()
      installedPackages.forEach { nameToPkg[it.name] = it }

      return installedPackages
        .asSequence()
        .filter { it.name.endsWith(STUBS_SUFFIX) && stubPkgsFilter(it) }
        .mapNotNull { stubPkg -> nameToPkg[stubPkg.name.removeSuffix(STUBS_SUFFIX)]?.let { it to stubPkg } }
        .filter {
          val runtimePkgName = it.first.name
          val requirement = it.second.requirements.firstOrNull { req -> req.name == runtimePkgName } ?: return@filter false

          requirement.match(listOf(it.first)) == null
        }
        .toList()
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoredStubPackages: MutableList<String> = mutableListOf()

  override fun createOptionsPanel(): JComponent = ListEditForm(PyPsiBundle.message("INSP.stub.packages.compatibility.ignored.packages"),
                                                               PyPsiBundle.message("INSP.stub.packages.compatibility.ignored.packages.label"),
                                                               ignoredStubPackages).contentPanel

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(ignoredStubPackages, holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(val ignoredStubPackages: MutableList<String>,
                        holder: ProblemsHolder,
                        context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyFile(node: PyFile) {
      val module = ModuleUtilCore.findModuleForFile(node) ?: return
      val sdk = PythonSdkUtil.findPythonSdk(module) ?: return

      val installedPackages = PyPackageManager.getInstance(sdk).packages ?: emptyList()
      if (installedPackages.isEmpty()) return

      val nameToPkg = mutableMapOf<String, PyPackage>()
      installedPackages.forEach { nameToPkg[it.name] = it }

      val status = node.project.getService(PyStubPackagesInstallingStatus::class.java)

      findIncompatibleRuntimeToStubPackages(
        sdk) { stubPkg ->
        stubPkg.name.let {
          !status.markedAsInstalling(it) && it !in ignoredStubPackages
        }
      }
        .forEach { (runtimePkg, stubPkg) ->
          val runtimePkgName = runtimePkg.name
          val requirement = stubPkg.requirements.firstOrNull { it.name == runtimePkgName } ?: return@forEach

          if (requirement.match(listOf(runtimePkg)) == null) {
            val stubPkgName = stubPkg.name
            val specsToString = StringUtil.join(requirement.versionSpecs, { it.presentableText }, ", ")
            val message = PyPsiBundle.message("INSP.stub.packages.compatibility.incompatible.packages.message",
                                              stubPkgName, PyRequirementRelation.EQ.presentableText, stubPkg.version,
                                              runtimePkgName, PyRequirementRelation.EQ.presentableText, runtimePkg.version,
                                              runtimePkgName, specsToString)
            registerProblem(node,
                            message,
                            PyInterpreterInspection.InterpreterSettingsQuickFix(module),
                            createIgnoreStubPackageQuickFix(stubPkgName, ignoredStubPackages))
          }
        }
    }

    private fun createIgnoreStubPackageQuickFix(stubPkgName: String, ignoredStubPkgs: MutableList<String>): LocalQuickFix {
      return object : LocalQuickFix {
        override fun getFamilyName() = PyPsiBundle.message("INSP.stub.packages.compatibility.ignore", stubPkgName)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
          if (ignoredStubPkgs.add(stubPkgName)) ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
        }
      }
    }
  }
}
