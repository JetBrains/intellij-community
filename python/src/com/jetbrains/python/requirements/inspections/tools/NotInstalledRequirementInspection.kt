// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.*
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.python.pyproject.PY_PROJECT_TOML_BUILD_SYSTEM
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsInspectionVisitor
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.quickfixes.*
import com.jetbrains.python.requirements.psi.Requirement
import com.jetbrains.python.sdk.isReadOnly
import org.toml.lang.psi.TomlTable

class NotInstalledRequirementInspection : LocalInspectionTool() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : RequirementsInspectionVisitor(holder, session) {

    override fun visitRequirementsFile(requirementsFile: RequirementsFile) {
      val psiFile = session.file
      val sdk = getPythonSdk(psiFile) ?: return

      if (isInBuildSystemToml(psiFile)) return
      if (handleEmptyFile(psiFile, holder)) return

      val packageManager = PythonPackageManager.forSdk(psiFile.project, sdk)
      val installedPackages = packageManager.listInstalledPackagesSnapshot()
        .map { PyPackage(it.name, it.version) }

      val notInstalled = findNotInstalledRequirements(requirementsFile, installedPackages)
      if (notInstalled.isEmpty()) return

      val isTomlInjection = isInjectedIntoToml(holder.file)
      registerProblems(holder, notInstalled, isTomlInjection, sdk)
    }
  }

  private fun isInBuildSystemToml(psiFile: com.intellij.psi.PsiFile): Boolean {
    val injectedElement = psiFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT) ?: return false
    val tableName = injectedElement.element?.findParentOfType<TomlTable>()?.header?.key?.text
    return tableName == PY_PROJECT_TOML_BUILD_SYSTEM
  }

  private fun handleEmptyFile(psiFile: com.intellij.psi.PsiFile, holder: ProblemsHolder): Boolean {
    if (psiFile.text.isNullOrBlank()) {
      val fixes = ModuleUtilCore.findModuleForPsiElement(psiFile)
                    ?.let { arrayOf(PyGenerateRequirementsFileQuickFix(it)) }
                  ?: emptyArray()
      holder.registerProblem(
        psiFile,
        PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        *fixes
      )
      return true
    }
    return false
  }

  private fun findNotInstalledRequirements(
    requirementsFile: RequirementsFile,
    installedPackages: List<PyPackage>
  ): List<Pair<Requirement, PyRequirement>> {
    return requirementsFile.requirements()
      .mapNotNull { req ->
        val parsed = PyRequirementParser.fromLine(req.text) ?: return@mapNotNull null
        if (parsed.match(installedPackages) != null) return@mapNotNull null
        req to parsed
      }
  }

  private fun isInjectedIntoToml(file: com.intellij.psi.PsiFile): Boolean {
    return file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT) != null
  }

  private fun getSingleRequirementQuickFix(
    pyRequirement: PyRequirement,
    isTomlInjection: Boolean
  ): LocalQuickFix {
    return if (isTomlInjection) {
      InstallRequirementInTomlQuickFix(pyRequirement)
    } else {
      InstallRequirementQuickFix(pyRequirement)
    }
  }

  private fun registerProblems(
    holder: ProblemsHolder,
    notInstalled: List<Pair<Requirement, PyRequirement>>,
    isTomlInjection: Boolean,
    sdk: Sdk
  ) {
    val unsatisfied = notInstalled.map { it.second }
    val canModify = !sdk.isReadOnly

    val installAllQuickFix = when {
      isTomlInjection -> InstallAllRequirementsInTomlQuickFix(unsatisfied)
      else -> InstallAllRequirementsQuickFix(unsatisfied)
    }.takeIf { canModify && unsatisfied.size > 1 }

    notInstalled.forEach { (psiRequirement, pyRequirement) ->
      val fixes = if (canModify) {
        listOfNotNull(getSingleRequirementQuickFix(pyRequirement, isTomlInjection), installAllQuickFix)
      } else {
        emptyList()
      }

      holder.registerProblem(
        psiRequirement,
        PyBundle.message("INSP.requirements.package.not.installed", psiRequirement.requirement),
        ProblemHighlightType.WARNING,
        *fixes.toTypedArray()
      )
    }
  }

  override fun isDumbAware(): Boolean = true
}