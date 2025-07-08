// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.quickfix.IgnoreRequirementFix
import com.jetbrains.python.inspections.quickfix.PyAddToDeclaredPackagesQuickFix
import com.jetbrains.python.inspections.quickfix.SyncProjectQuickFix
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageManagerModuleHelpers
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyRequirementVisitor(
  holder: ProblemsHolder?,
  val ignoredPackages: Collection<String>,
  context: TypeEvalContext,
) : PyInspectionVisitor(holder, context) {
  override fun visitPyFromImportStatement(node: PyFromImportStatement) {
    val importSource = node.importSource ?: return
    checkPackageNameInRequirements(importSource)
  }

  override fun visitPyImportStatement(node: PyImportStatement) {
    node.importElements.mapNotNull { it.importReferenceExpression }.forEach { checkPackageNameInRequirements(it) }
  }

  private fun checkPackageNameInRequirements(importedExpression: PyQualifiedExpression) {
    if (PyInspectionExtension.EP_NAME.extensionList.any { it.ignorePackageNameInRequirements(importedExpression) }) {
      return
    }

    val packageReferenceExpression = PyPsiUtils.getFirstQualifier(importedExpression)
    val importedPyModule = packageReferenceExpression.name ?: return
    val module: Module = ModuleUtilCore.findModuleForPsiElement(packageReferenceExpression) ?: return

    if (PyPackageManagerModuleHelpers.isLocalModule(packageReferenceExpression, module)) {
      return
    }

    val sdk = module.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(module.project, sdk)
    val requirementsManager = manager.getDependencyManager() ?: return
    if (requirementsManager.getDependenciesFile() == null)
      return
    val installedNotDeclaredChecker = InstalledButNotDeclaredChecker(ignoredPackages, manager)
    val packageName = installedNotDeclaredChecker.getUndeclaredPackageName(importedPyModule = importedPyModule) ?: return

    registerProblem(
      packageReferenceExpression,
      PyPsiBundle.message(PACKAGE_NOT_LISTED, importedPyModule),
      ProblemHighlightType.WEAK_WARNING,
      null,
      PyAddToDeclaredPackagesQuickFix(requirementsManager, packageName),
      IgnoreRequirementFix(setOf(packageName))
    )
  }

  override fun visitPyFile(node: PyFile) {
    val module = ModuleUtilCore.findModuleForPsiElement(node) ?: return
    checkPackagesHaveBeenInstalled(node, module)
  }

  private fun checkPackagesHaveBeenInstalled(file: PsiElement, module: Module) {
    if (PyPackageManagerModuleHelpers.isRunningPackagingTasks(module))
      return
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return
    val manager = PythonPackageManager.forSdk(module.project, sdk)

    val declaredNotInstalledChecker = DeclaredButNotInstalledPackagesChecker(ignoredPackages)
    val unsatisfied = declaredNotInstalledChecker.findUnsatisfiedRequirements(module, manager)
    if (unsatisfied.isEmpty())
      return

    val requirementsList = PyPackageUtil.requirementsToString(unsatisfied)
    val message = PyPsiBundle.message(REQUIREMENT_NOT_SATISFIED, requirementsList, unsatisfied.size)

    val ignoreFix = IgnoreRequirementFix(unsatisfied.mapTo(mutableSetOf()) { it.presentableTextWithoutVersion })
    val quickFixes = listOf(SyncProjectQuickFix(), ignoreFix)

    registerProblem(
      file,
      message,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      null,
      *quickFixes.toTypedArray()
    )
  }


  companion object {
    private const val REQUIREMENT_NOT_SATISFIED = "INSP.requirements.package.requirements.not.satisfied"
    private const val PACKAGE_NOT_LISTED = "INSP.requirements.package.containing.module.not.listed.in.project.requirements"
  }
}